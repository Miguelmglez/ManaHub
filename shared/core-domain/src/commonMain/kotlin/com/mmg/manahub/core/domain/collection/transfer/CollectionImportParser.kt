package com.mmg.manahub.core.domain.collection.transfer

import com.mmg.manahub.feature.decks.domain.engine.DeckImportExportHelper

/**
 * Parses a pasted list or an imported file into [CollectionImportLine]s. The format is detected
 * from the first record: a CSV header naming Moxfield or ManaBox columns, otherwise text lines.
 *
 * Identical lines (same identifier, foil, condition and language) are merged by summing their
 * quantity, capped at [MAX_QUANTITY_PER_LINE].
 */
object CollectionImportParser {

    const val MAX_QUANTITY_PER_LINE = 9_999

    /**
     * Caps on what is reported BACK to the user. Both lines and CSV records are echoed verbatim —
     * into UI state, a persisted preference file and a clipboard Intent — and none of those can
     * take an arbitrary slice of a picked file: `CsvCodec` folds everything after an unterminated
     * quote into ONE record, and a file of single-character lines yields one entry per line.
     */
    const val MAX_REJECTED_LINES = 200
    const val MAX_REPORTED_LINE_LENGTH = 300

    private const val BINARY_SAMPLE_CHARS = 64 * 1024
    private const val BINARY_SUSPECT_PERCENT = 5

    private const val CANCELLATION_CHECK_LINES = 1_000

    private val SECTION_HEADERS = setOf(
        "commander", "commanders", "deck", "mainboard", "main", "sideboard", "side", "sb",
        "maybeboard", "maybe", "companion", "companions", "tokens", "considering",
    )

    private object MoxfieldColumns {
        const val COUNT = "count"
        const val NAME = "name"
        const val EDITION = "edition"
        const val CONDITION = "condition"
        const val LANGUAGE = "language"
        const val FOIL = "foil"
        const val COLLECTOR_NUMBER = "collector number"
    }

    private object ManaBoxColumns {
        const val NAME = "name"
        const val SET_CODE = "set code"
        const val COLLECTOR_NUMBER = "collector number"
        const val FOIL = "foil"
        const val QUANTITY = "quantity"
        const val SCRYFALL_ID = "scryfall id"
        const val CONDITION = "condition"
        const val LANGUAGE = "language"
    }

    /**
     * @param ensureActive called every [CANCELLATION_CHECK_LINES] lines. Parsing a picked file is
     *   CPU-bound and not itself suspending, so this is the only way dismissing the sheet frees the
     *   thread instead of running the whole document to completion. Callers inside a coroutine pass
     *   their context's `ensureActive`.
     */
    fun parse(text: String, ensureActive: () -> Unit = {}): ParsedCollectionImport {
        val clean = dropPreamble(text.removePrefix("﻿"))
        // A CSV re-saved by Excel starts with `sep=,` and some exporters add a title row, either of
        // which would otherwise push the real header out of view and reject every row as TEXT.
        val header = clean.lineSequence().filter { it.isNotBlank() }.take(2)
            .firstOrNull { detectFormat(it) != CollectionFileFormat.TEXT }
            ?: return parseText(clean, ensureActive)
        val csv = dropLinesBefore(clean, header)
        return when (detectFormat(header)) {
            CollectionFileFormat.MANABOX_CSV -> parseManaBoxCsv(csv, ensureActive)
            else -> parseMoxfieldCsv(csv, ensureActive)
        }
    }

    /**
     * True when [text] is almost certainly not a card list but a binary file decoded as UTF-8.
     *
     * The document picker has to accept `* / *` (providers routinely mislabel `.csv`), so a picked
     * JPEG reaches the parser as mojibake rather than an error, and the user is told "no card lines
     * were recognized" for a file that was never text. Replacement characters and C0 controls are
     * the giveaway; a genuine list has effectively none.
     */
    fun looksBinary(text: String): Boolean {
        val sample = if (text.length <= BINARY_SAMPLE_CHARS) text else text.substring(0, BINARY_SAMPLE_CHARS)
        if (sample.isEmpty()) return false
        val suspect = sample.count { c ->
            c == '�' || (c.code < 0x20 && c != '\n' && c != '\r' && c != '\t')
        }
        return suspect * 100 > sample.length * BINARY_SUSPECT_PERCENT
    }

    /** A source line trimmed to what is safe to show, persist and copy. */
    internal fun String.asReportedLine(): String =
        if (length <= MAX_REPORTED_LINE_LENGTH) this else substring(0, MAX_REPORTED_LINE_LENGTH) + "…"

    /**
     * The lines worth showing back, plus how many there really were. The list is bounded by
     * [MAX_REJECTED_LINES]; [total] is not, so the UI can say "showing 200 of N" instead of
     * quietly reporting 200.
     */
    private class RejectedLines {
        val lines = mutableListOf<String>()
        var total = 0
            private set

        fun add(line: String) {
            total++
            if (lines.size < MAX_REJECTED_LINES) lines += line.asReportedLine()
        }
    }

    /**
     * Drops the leading blank, comment and `sep=,` lines an exporter may put above the header.
     * Walks the source by index: `lines()` here would materialise the whole document.
     */
    private fun dropPreamble(text: String): String {
        var start = 0
        while (start < text.length) {
            val lineEnd = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            val trimmed = text.substring(start, lineEnd).trim()
            val isPreamble = trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#") ||
                SEP_PREAMBLE_REGEX.matches(trimmed)
            if (!isPreamble) return if (start == 0) text else text.substring(start)
            start = lineEnd + 1
        }
        return text
    }

    /** Everything from [header] onwards, so the CSV reader sees the header as its first record. */
    private fun dropLinesBefore(text: String, header: String): String {
        var from = 0
        while (from < text.length) {
            val index = text.indexOf(header, from)
            if (index <= 0) return text
            // Only a match at a line boundary is the header line itself.
            if (text[index - 1] == '\n' || text[index - 1] == '\r') return text.substring(index)
            from = index + 1
        }
        return text
    }

    internal fun detectFormat(firstLine: String): CollectionFileFormat {
        val header = CsvCodec.parse(firstLine).firstOrNull()?.fields
            ?.map { it.trim().lowercase() }
            ?.toSet()
            ?: return CollectionFileFormat.TEXT
        return when {
            ManaBoxColumns.QUANTITY in header && ManaBoxColumns.NAME in header &&
                (ManaBoxColumns.SET_CODE in header || ManaBoxColumns.SCRYFALL_ID in header) ->
                CollectionFileFormat.MANABOX_CSV
            MoxfieldColumns.COUNT in header && MoxfieldColumns.NAME in header ->
                CollectionFileFormat.MOXFIELD_CSV
            else -> CollectionFileFormat.TEXT
        }
    }

    private fun parseText(text: String, ensureActive: () -> Unit): ParsedCollectionImport {
        val lines = mutableListOf<CollectionImportLine>()
        val rejected = RejectedLines()
        var scanned = 0
        for (raw in text.lineSequence()) {
            if (++scanned > DeckImportExportHelper.MAX_SCANNED_LINES) break
            if (scanned % CANCELLATION_CHECK_LINES == 0) ensureActive()
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) continue
            if (isSectionHeader(line)) continue
            val parsed = DeckImportExportHelper.parseCardLine(line)
            if (parsed == null || parsed.quantity <= 0) {
                rejected.add(line)
                continue
            }
            lines += CollectionImportLine(
                quantity = parsed.quantity,
                name = parsed.name,
                setCode = parsed.setCode?.lowercase(),
                collectorNumber = parsed.collectorNumber,
                scryfallId = null,
                isFoil = parsed.isFoil,
                condition = CollectionCardAttributes.DEFAULT_CONDITION,
                language = CollectionCardAttributes.DEFAULT_LANGUAGE,
                // rawLine is what reaches unresolvedLines, so it inherits the report cap too.
                rawLine = line.asReportedLine(),
            )
        }
        val merged = merge(lines)
        return ParsedCollectionImport(
            format = CollectionFileFormat.TEXT,
            lines = merged.lines,
            rejectedLines = rejected.lines,
            clampedCopies = merged.clampedCopies,
            rejectedCount = rejected.total,
        )
    }

    // "Sideboard", "SIDEBOARD:" and Arena's "Commander (1)" style headers.
    private fun isSectionHeader(line: String): Boolean {
        val key = line.lowercase().trimEnd(':').substringBefore('(').trim()
        return key in SECTION_HEADERS
    }

    private fun parseMoxfieldCsv(text: String, ensureActive: () -> Unit): ParsedCollectionImport =
        parseCsv(text, CollectionFileFormat.MOXFIELD_CSV, ensureActive) { row ->
            val name = row[MoxfieldColumns.NAME]
            val setCode = row[MoxfieldColumns.EDITION]
            CsvLine(
                quantity = row[MoxfieldColumns.COUNT]?.toIntOrNull(),
                name = name,
                setCode = setCode,
                collectorNumber = row[MoxfieldColumns.COLLECTOR_NUMBER],
                scryfallId = null,
                foil = row[MoxfieldColumns.FOIL],
                condition = row[MoxfieldColumns.CONDITION],
                language = row[MoxfieldColumns.LANGUAGE],
            )
        }

    private fun parseManaBoxCsv(text: String, ensureActive: () -> Unit): ParsedCollectionImport =
        parseCsv(text, CollectionFileFormat.MANABOX_CSV, ensureActive) { row ->
            CsvLine(
                quantity = row[ManaBoxColumns.QUANTITY]?.toIntOrNull(),
                name = row[ManaBoxColumns.NAME],
                setCode = row[ManaBoxColumns.SET_CODE],
                collectorNumber = row[ManaBoxColumns.COLLECTOR_NUMBER],
                scryfallId = row[ManaBoxColumns.SCRYFALL_ID],
                foil = row[ManaBoxColumns.FOIL],
                condition = row[ManaBoxColumns.CONDITION],
                language = row[ManaBoxColumns.LANGUAGE],
            )
        }

    private class CsvLine(
        val quantity: Int?,
        val name: String?,
        val setCode: String?,
        val collectorNumber: String?,
        val scryfallId: String?,
        val foil: String?,
        val condition: String?,
        val language: String?,
    )

    private inline fun parseCsv(
        text: String,
        format: CollectionFileFormat,
        ensureActive: () -> Unit,
        read: (Map<String, String>) -> CsvLine,
    ): ParsedCollectionImport {
        val records = CsvCodec.parse(text)
        if (records.isEmpty()) return ParsedCollectionImport(format, emptyList(), emptyList())
        val header = records.first().fields.map { it.trim().lowercase() }
        val lines = mutableListOf<CollectionImportLine>()
        val rejected = RejectedLines()
        var scanned = 0
        for (record in records.drop(1)) {
            if (++scanned > DeckImportExportHelper.MAX_SCANNED_LINES) break
            if (scanned % CANCELLATION_CHECK_LINES == 0) ensureActive()
            val row = header.indices.associate { i -> header[i] to record.fields.getOrElse(i) { "" }.trim() }
                .filterValues { it.isNotEmpty() }
            val csv = read(row)
            val quantity = csv.quantity
            val scryfallId = csv.scryfallId?.lowercase()?.takeIf { SCRYFALL_ID_REGEX.matches(it) }
            val hasPrinting = !csv.setCode.isNullOrBlank() && !csv.collectorNumber.isNullOrBlank()
            if (quantity == null || quantity <= 0 || (csv.name == null && scryfallId == null && !hasPrinting)) {
                rejected.add(record.raw.trim())
                continue
            }
            lines += CollectionImportLine(
                quantity = quantity,
                name = csv.name,
                setCode = csv.setCode?.lowercase(),
                collectorNumber = csv.collectorNumber,
                scryfallId = scryfallId,
                isFoil = isFoilValue(csv.foil),
                condition = CollectionCardAttributes.conditionCode(csv.condition),
                language = CollectionCardAttributes.languageCode(csv.language),
                rawLine = record.raw.trim().asReportedLine(),
            )
        }
        val merged = merge(lines)
        return ParsedCollectionImport(
            format = format,
            lines = merged.lines,
            rejectedLines = rejected.lines,
            clampedCopies = merged.clampedCopies,
            rejectedCount = rejected.total,
        )
    }

    // Moxfield writes "foil"/"etched"/"", ManaBox "normal"/"foil"/"etched"; etched counts as foil.
    private fun isFoilValue(raw: String?): Boolean = when (raw?.trim()?.lowercase()) {
        "foil", "etched", "true", "yes", "1" -> true
        else -> false
    }

    /** [merge]d lines plus the copies the [MAX_QUANTITY_PER_LINE] cap had to drop. */
    internal class MergeResult(val lines: List<CollectionImportLine>, val clampedCopies: Int)

    /** Merges lines sharing identifier + foil + condition + language, summing their quantity. */
    internal fun merge(lines: List<CollectionImportLine>): MergeResult {
        val merged = LinkedHashMap<String, CollectionImportLine>()
        // Long: a CSV quantity is only bounded by Int.MAX_VALUE, so two rows overflow a clamp
        // counted in Int, and the count reaches a plural resource and a telemetry key.
        var clamped = 0L
        for (line in lines) {
            val key = mergeKey(line)
            val existing = merged[key]
            val wanted = (existing?.quantity?.toLong() ?: 0L) + line.quantity
            val capped = wanted.coerceAtMost(MAX_QUANTITY_PER_LINE.toLong())
            clamped += wanted - capped
            merged[key] = (existing ?: line).copy(quantity = capped.toInt())
        }
        return MergeResult(merged.values.toList(), clamped.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    private fun mergeKey(line: CollectionImportLine): String {
        val identifier = when {
            line.scryfallId != null -> "id:${line.scryfallId}"
            line.setCode != null && line.collectorNumber != null ->
                "print:${line.setCode}:${line.collectorNumber.lowercase()}"
            else -> "name:${line.name?.lowercase()}:${line.setCode.orEmpty()}"
        }
        return "$identifier|${line.isFoil}|${line.condition}|${line.language}"
    }

    private val SCRYFALL_ID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    // Excel / LibreOffice write this above the header when the list separator is not a comma.
    private val SEP_PREAMBLE_REGEX = Regex("^sep=.$", RegexOption.IGNORE_CASE)
}
