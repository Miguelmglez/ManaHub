package com.mmg.manahub.core.domain.collection.transfer

/** Minimal RFC 4180 CSV reader/writer: quoted fields, doubled quotes, and quoted line breaks. */
internal object CsvCodec {

    /** A parsed record plus the source text it came from (for the unresolved-lines report). */
    data class Record(val fields: List<String>, val raw: String)

    /** Splits [text] into records; blank lines are skipped. */
    fun parse(text: String): List<Record> {
        val records = mutableListOf<Record>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        val raw = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endRecord() {
            fields += field.toString()
            field.clear()
            if (fields.size > 1 || fields[0].isNotBlank()) {
                records += Record(fields.toList(), raw.toString())
            }
            fields.clear()
            raw.clear()
        }

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                raw.append(c)
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        raw.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> { inQuotes = true; raw.append(c) }
                    ',' -> { fields += field.toString(); field.clear(); raw.append(c) }
                    '\r' -> Unit
                    '\n' -> endRecord()
                    else -> { field.append(c); raw.append(c) }
                }
            }
            i++
        }
        if (field.isNotEmpty() || fields.isNotEmpty()) endRecord()
        return records
    }

    /** Quotes [value] when it holds a delimiter, a quote or a line break. */
    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** Joins [values] into one CSV row. */
    fun row(values: List<String>): String = values.joinToString(",") { escape(it) }
}
