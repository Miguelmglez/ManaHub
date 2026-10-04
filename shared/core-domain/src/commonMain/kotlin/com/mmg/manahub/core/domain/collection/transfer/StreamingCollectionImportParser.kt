package com.mmg.manahub.core.domain.collection.transfer

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Reads at most [length] decoded characters; zero denotes EOF. */
fun interface TransferCharacterSource {
    suspend fun read(buffer: CharArray, length: Int): Int
}

/** UTF-8 byte range excluding the terminating newline, suitable for source reconstruction. */
data class TransferSourceRange(val start: Long, val endExclusive: Long)

/** Every physical TXT line or logical CSV record has a stable one-based ordinal. */
data class TransferParsedRecord(
    val ordinal: Long,
    val range: TransferSourceRange,
    val kind: TransferRecordKind,
    val line: CollectionImportLine?,
    val error: TransferParseError?,
    val preview: String,
)

/** Accounting categories retain ignored format records independently of data records. */
enum class TransferRecordKind { PREAMBLE, HEADER, DATA }

/** Bounded categories contain no source text or external exception. */
enum class TransferParseError {
    INVALID_QUANTITY, INVALID_IDENTIFIER, INVALID_ATTRIBUTES, INVALID_ROW,
    INVALID_HEADER, INVALID_CSV, UNSUPPORTED_SEPARATOR, RECORD_TOO_LARGE, TOO_MANY_COLUMNS, BINARY_CONTENT,
}

/** Staged rows are never eligible for review/application until [completed] succeeds. */
interface TransferRecordSink {
    suspend fun record(record: TransferParsedRecord)
    suspend fun completed(summary: TransferParseSummary)
    suspend fun rejected(error: TransferParseError)
}

/** Counters include every record; selection limits do not truncate the source. */
data class TransferParseSummary(
    val format: CollectionFileFormat,
    val records: Long,
    val preambleRecords: Long,
    val headerRecords: Long,
    val dataRecords: Long,
    val validRecords: Long,
    val invalidRecords: Long,
    val copies: Long,
    val unrepresentedColumns: List<String>,
) {
    val exceedsSelectionBudget: Boolean
        get() = dataRecords > TransferLimits.MAX_DATA_RECORDS || preambleRecords > TransferLimits.MAX_PREAMBLE_RECORDS
}

/** Resource limits shared by preparation and review; byte limits are inclusive. */
object TransferLimits {
    const val CHARACTER_BUFFER_SIZE = 64 * 1024
    const val MAX_RECORD_BYTES = 1024 * 1024
    const val MAX_COLUMNS = 256
    const val MAX_DATA_RECORDS = 100_000L
    const val MAX_PREAMBLE_RECORDS = 100_000L
    const val MAX_FILE_BYTES = 50L * 1024 * 1024
    const val MAX_BATCH_BYTES = 100L * 1024 * 1024
    const val MAX_FILES = 10
    const val REVIEW_PAGE_SIZE = 50
    const val WRITE_BATCH_SIZE = 500
}

/** Streams into uncommitted file staging; structural failure invalidates every staged prefix row. */
class StreamingCollectionImportParser {
    suspend fun parse(source: TransferCharacterSource, sink: TransferRecordSink): TransferParseSummary? {
        val scanner = Scanner(sink)
        val buffer = CharArray(TransferLimits.CHARACTER_BUFFER_SIZE)
        return try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = source.read(buffer, buffer.size)
                require(count in 0..buffer.size)
                if (count == 0) break
                for (index in 0 until count) {
                    if (index % 1024 == 0) currentCoroutineContext().ensureActive()
                    scanner.character(buffer[index])
                }
            }
            scanner.finish().also { sink.completed(it) }
        } catch (failure: StructuralFailure) {
            sink.rejected(failure.category)
            null
        }
    }

    private class StructuralFailure(val category: TransferParseError) : Exception()

    private class Scanner(private val sink: TransferRecordSink) {
        private val raw = StringBuilder()
        private var offset = 0L
        private var start = 0L
        private var pendingCr = false
        private var highSurrogate = false
        private var quoted = false
        private var afterQuote = false
        private var fieldStart = true
        private var columns = 1
        private var delimiter = ','
        private var format: CollectionFileFormat? = null
        private var header: List<String> = emptyList()
        private var title: Pair<String, TransferSourceRange>? = null
        private var ordinal = 0L
        private var preamble = 0L
        private var headers = 0L
        private var data = 0L
        private var valid = 0L
        private var invalid = 0L
        private var copies = 0L

        suspend fun character(char: Char) {
            if (highSurrogate && !char.isLowSurrogate()) fail(TransferParseError.BINARY_CONTENT)
            val bytes = when {
                char.isHighSurrogate() -> { highSurrogate = true; 0 }
                char.isLowSurrogate() -> {
                    if (!highSurrogate) fail(TransferParseError.BINARY_CONTENT)
                    highSurrogate = false
                    4
                }
                char.code < 0x80 -> 1
                char.code < 0x800 -> 2
                else -> 3
            }
            if (offset == 0L && char == '\uFEFF') {
                offset += bytes
                start = offset
                return
            }
            if (char.code < 0x20 && char !in "\t\r\n") fail(TransferParseError.BINARY_CONTENT)
            if (pendingCr) {
                pendingCr = false
                if (char == '\n') { offset += bytes; start = offset; return }
            }
            if (!quoted && char in "\r\n") {
                emit(TransferSourceRange(start, offset))
                offset += bytes
                start = offset
                pendingCr = char == '\r'
                return
            }
            if (offset + bytes - start > TransferLimits.MAX_RECORD_BYTES) fail(TransferParseError.RECORD_TOO_LARGE)
            offset += bytes
            raw.append(char)
            if (format == CollectionFileFormat.TEXT) return
            when {
                quoted && char == '"' -> { quoted = false; afterQuote = true }
                quoted -> Unit
                afterQuote && char == '"' -> { quoted = true; afterQuote = false }
                afterQuote && char == delimiter -> { afterQuote = false; fieldStart = true; column() }
                afterQuote && !char.isWhitespace() -> fail(TransferParseError.INVALID_CSV)
                char == '"' && fieldStart -> { quoted = true; fieldStart = false }
                char == delimiter -> { fieldStart = true; column() }
                !char.isWhitespace() -> fieldStart = false
            }
        }

        private fun column() {
            columns++
            if (columns > TransferLimits.MAX_COLUMNS) fail(TransferParseError.TOO_MANY_COLUMNS)
        }

        suspend fun finish(): TransferParseSummary {
            if (highSurrogate) fail(TransferParseError.BINARY_CONTENT)
            if (quoted) fail(TransferParseError.INVALID_CSV)
            if (raw.isNotEmpty()) emit(TransferSourceRange(start, offset))
            title?.let { (text, range) ->
                format = CollectionFileFormat.TEXT
                textRecord(text, range)
            }
            title = null
            return TransferParseSummary(
                format ?: CollectionFileFormat.TEXT, ordinal, preamble, headers, data, valid, invalid,
                copies, header.filter { it !in REPRESENTED_COLUMNS },
            )
        }

        private suspend fun emit(range: TransferSourceRange) {
            val text = raw.toString()
            raw.clear()
            quoted = false
            afterQuote = false
            fieldStart = true
            columns = 1
            val trimmed = text.trim()
            if (format == null) {
                if (isPreamble(trimmed)) {
                    title?.let { (value, position) ->
                        format = CollectionFileFormat.TEXT
                        textRecord(value, position)
                    }
                    title = null
                    if (trimmed.startsWith("sep=", ignoreCase = true) && trimmed.length == 5) {
                        delimiter = trimmed.last()
                        if (delimiter != ',') fail(TransferParseError.UNSUPPORTED_SEPARATOR)
                    }
                    publish(text, range, TransferRecordKind.PREAMBLE)
                    return
                }
                val fields = csvFields(text, delimiter)
                val names = fields.map { it.trim().lowercase() }
                val detected = when {
                    "quantity" in names && "name" in names && ("set code" in names || "scryfall id" in names) -> CollectionFileFormat.MANABOX_CSV
                    "count" in names && "name" in names -> CollectionFileFormat.MOXFIELD_CSV
                    else -> null
                }
                if (detected != null) {
                    if (names.any { it.isEmpty() } || names.distinct().size != names.size) fail(TransferParseError.INVALID_HEADER)
                    title?.let { (value, position) -> publish(value, position, TransferRecordKind.PREAMBLE) }
                    title = null
                    format = detected
                    header = names
                    publish(text, range, TransferRecordKind.HEADER)
                    return
                }
                if ("name" in names && names.any { it in REPRESENTED_COLUMNS - "name" }) fail(TransferParseError.INVALID_HEADER)
                if (title == null && parseText(trimmed) == null && !isSection(trimmed)) {
                    title = text to range
                    return
                }
                format = CollectionFileFormat.TEXT
                title?.let { (value, position) -> textRecord(value, position) }
                title = null
            }
            if (format == CollectionFileFormat.TEXT) textRecord(text, range)
            else if (trimmed.isEmpty() || trimmed.startsWith('#') || trimmed.startsWith("//")) {
                publish(text, range, TransferRecordKind.PREAMBLE)
            } else csvRecord(text, range)
        }

        private suspend fun textRecord(text: String, range: TransferSourceRange) {
            val trimmed = text.trim()
            if (isPreamble(trimmed) || isSection(trimmed)) publish(text, range, TransferRecordKind.PREAMBLE)
            else {
                val parsed = parseText(trimmed)
                publish(text, range, TransferRecordKind.DATA, parsed, if (parsed == null) TransferParseError.INVALID_ROW else null)
            }
        }

        private suspend fun csvRecord(text: String, range: TransferSourceRange) {
            val fields = csvFields(text, delimiter)
            if (fields.size != header.size) {
                publish(text, range, TransferRecordKind.DATA, error = TransferParseError.INVALID_ROW)
                return
            }
            val row = header.indices.associate { header[it] to fields[it].trim() }
            val quantity = row[if (format == CollectionFileFormat.MANABOX_CSV) "quantity" else "count"]?.toIntOrNull()
            val id = row["scryfall id"].nonblank()?.lowercase()
            val name = row["name"].nonblank()
            val set = row[if (format == CollectionFileFormat.MANABOX_CSV) "set code" else "edition"].nonblank()?.lowercase()
            val collector = row["collector number"].nonblank()
            val condition = CollectionCardAttributes.conditionCodeOrNull(row["condition"])
            val language = CollectionCardAttributes.languageCodeOrNull(row["language"])
            val error = when {
                quantity == null || quantity <= 0 -> TransferParseError.INVALID_QUANTITY
                id != null && !UUID.matches(id) -> TransferParseError.INVALID_IDENTIFIER
                id == null && name == null && (set == null || collector == null) -> TransferParseError.INVALID_IDENTIFIER
                row["foil"].nonblank()?.lowercase() !in FOIL_VALUES -> TransferParseError.INVALID_ATTRIBUTES
                condition == null || language == null -> TransferParseError.INVALID_ATTRIBUTES
                else -> null
            }
            val parsed = if (error == null) CollectionImportLine(
                quantity!!, name, set, collector, id,
                row["foil"]?.lowercase() in setOf("foil", "etched", "true", "yes", "1"),
                condition!!, language!!, text.take(300),
            ) else null
            publish(text, range, TransferRecordKind.DATA, parsed, error)
        }

        private suspend fun publish(
            text: String, range: TransferSourceRange, kind: TransferRecordKind,
            line: CollectionImportLine? = null, error: TransferParseError? = null,
        ) {
            ordinal++
            when (kind) {
                TransferRecordKind.PREAMBLE -> preamble++
                TransferRecordKind.HEADER -> headers++
                TransferRecordKind.DATA -> {
                    data++
                    if (line != null) { valid++; copies += line.quantity.toLong() } else invalid++
                }
            }
            sink.record(TransferParsedRecord(ordinal, range, kind, line, error, text.take(300)))
        }
    }

    private companion object {
        val UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        val REPRESENTED_COLUMNS = setOf("quantity", "count", "name", "set code", "edition", "collector number", "scryfall id", "foil", "condition", "language")
        val FOIL_VALUES = setOf(null, "normal", "nonfoil", "non-foil", "false", "no", "0", "foil", "etched", "true", "yes", "1")
        val SECTIONS = setOf("commander", "commanders", "deck", "mainboard", "main", "sideboard", "side", "sb", "maybeboard", "maybe", "companion", "companions", "tokens", "considering")
        fun fail(error: TransferParseError): Nothing = throw StructuralFailure(error)
        fun String?.nonblank(): String? = this?.takeIf { it.isNotBlank() }
        fun isSection(text: String): Boolean = text.lowercase().trimEnd(':').substringBefore('(').trim() in SECTIONS
        fun isPreamble(text: String): Boolean = text.isEmpty() || text.startsWith('#') || text.startsWith("//") || (text.startsWith("sep=", true) && text.length == 5)

        fun parseText(text: String): CollectionImportLine? {
            val split = text.indexOfFirst { it.isWhitespace() }
            if (split <= 0) return null
            val quantity = text.substring(0, split).removeSuffix("x").removeSuffix("X").removeSuffix("×").toIntOrNull()?.takeIf { it > 0 } ?: return null
            var name = text.substring(split).trim()
            val foil = name.endsWith("*F*", true) || name.endsWith("*E*", true)
            if (foil) name = name.dropLast(3).trimEnd()
            var set: String? = null
            var collector: String? = null
            val open = name.lastIndexOf(" (")
            if (open >= 0) {
                val close = name.indexOf(')', open + 2)
                if (close >= 0) {
                    val suffix = name.substring(close + 1).trim()
                    if (suffix.isEmpty() || suffix.none { it.isWhitespace() }) {
                        set = name.substring(open + 2, close).nonblank()?.lowercase()
                        collector = suffix.nonblank()
                        name = name.substring(0, open).trimEnd()
                    }
                }
            }
            if (name.isEmpty()) return null
            return CollectionImportLine(quantity, name, set, collector, null, foil, "NM", "en", text.take(300))
        }

        fun csvFields(text: String, delimiter: Char): List<String> {
            val fields = mutableListOf<String>()
            val value = StringBuilder()
            var quoted = false
            var closed = false
            var index = 0
            while (index < text.length) {
                val char = text[index++]
                when {
                    quoted && char == '"' -> {
                        if (index < text.length && text[index] == '"') { value.append('"'); index++ }
                        else { quoted = false; closed = true }
                    }
                    quoted -> value.append(char)
                    char == delimiter -> { fields += value.toString(); value.clear(); closed = false }
                    closed && !char.isWhitespace() -> fail(TransferParseError.INVALID_CSV)
                    closed -> Unit
                    char == '"' && value.isBlank() -> { value.clear(); quoted = true }
                    else -> value.append(char)
                }
                if (fields.size >= TransferLimits.MAX_COLUMNS) fail(TransferParseError.TOO_MANY_COLUMNS)
            }
            if (quoted) fail(TransferParseError.INVALID_CSV)
            fields += value.toString()
            return fields
        }
    }
}
