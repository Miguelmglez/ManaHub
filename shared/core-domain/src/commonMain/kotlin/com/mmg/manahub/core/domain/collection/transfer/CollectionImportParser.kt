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

    fun parse(text: String): ParsedCollectionImport {
        val clean = text.removePrefix("﻿")
        val firstLine = clean.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        return when (detectFormat(firstLine)) {
            CollectionFileFormat.MOXFIELD_CSV -> parseMoxfieldCsv(clean)
            CollectionFileFormat.MANABOX_CSV -> parseManaBoxCsv(clean)
            CollectionFileFormat.TEXT -> parseText(clean)
        }
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

    private fun parseText(text: String): ParsedCollectionImport {
        val lines = mutableListOf<CollectionImportLine>()
        val rejected = mutableListOf<String>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) continue
            if (isSectionHeader(line)) continue
            val parsed = DeckImportExportHelper.parseCardLine(line)
            if (parsed == null || parsed.quantity <= 0) {
                rejected += line
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
                rawLine = line,
            )
        }
        val merged = merge(lines)
        return ParsedCollectionImport(CollectionFileFormat.TEXT, merged.lines, rejected, merged.clampedCopies)
    }

    // "Sideboard", "SIDEBOARD:" and Arena's "Commander (1)" style headers.
    private fun isSectionHeader(line: String): Boolean {
        val key = line.lowercase().trimEnd(':').substringBefore('(').trim()
        return key in SECTION_HEADERS
    }

    private fun parseMoxfieldCsv(text: String): ParsedCollectionImport =
        parseCsv(text, CollectionFileFormat.MOXFIELD_CSV) { row ->
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

    private fun parseManaBoxCsv(text: String): ParsedCollectionImport =
        parseCsv(text, CollectionFileFormat.MANABOX_CSV) { row ->
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
        read: (Map<String, String>) -> CsvLine,
    ): ParsedCollectionImport {
        val records = CsvCodec.parse(text)
        if (records.isEmpty()) return ParsedCollectionImport(format, emptyList(), emptyList())
        val header = records.first().fields.map { it.trim().lowercase() }
        val lines = mutableListOf<CollectionImportLine>()
        val rejected = mutableListOf<String>()
        for (record in records.drop(1)) {
            val row = header.indices.associate { i -> header[i] to record.fields.getOrElse(i) { "" }.trim() }
                .filterValues { it.isNotEmpty() }
            val csv = read(row)
            val quantity = csv.quantity
            val scryfallId = csv.scryfallId?.lowercase()?.takeIf { SCRYFALL_ID_REGEX.matches(it) }
            val hasPrinting = !csv.setCode.isNullOrBlank() && !csv.collectorNumber.isNullOrBlank()
            if (quantity == null || quantity <= 0 || (csv.name == null && scryfallId == null && !hasPrinting)) {
                rejected += record.raw.trim()
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
                rawLine = record.raw.trim(),
            )
        }
        val merged = merge(lines)
        return ParsedCollectionImport(format, merged.lines, rejected, merged.clampedCopies)
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
        var clamped = 0
        for (line in lines) {
            val key = mergeKey(line)
            val existing = merged[key]
            val wanted = (existing?.quantity?.toLong() ?: 0L) + line.quantity
            val capped = wanted.coerceAtMost(MAX_QUANTITY_PER_LINE.toLong())
            clamped += (wanted - capped).toInt()
            merged[key] = (existing ?: line).copy(quantity = capped.toInt())
        }
        return MergeResult(merged.values.toList(), clamped)
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
}
