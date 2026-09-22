package com.mmg.manahub.core.domain.collection.transfer

import com.mmg.manahub.feature.decks.domain.engine.DeckImportExportHelper
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Serialises [CollectionExportEntry] rows into the three [CollectionFileFormat]s. Every output
 * parses back through [CollectionImportParser] to the same entries (Moxfield's condition scale is
 * coarser than the app's, see [CollectionCardAttributes.moxfieldCondition]).
 */
object CollectionExportFormatter {

    val MOXFIELD_HEADER = listOf(
        "Count", "Tradelist Count", "Name", "Edition", "Condition", "Language", "Foil", "Tags",
        "Last Modified", "Collector Number", "Alter", "Proxy", "Purchase Price",
    )

    val MANABOX_HEADER = listOf(
        "Name", "Set code", "Set name", "Collector number", "Foil", "Rarity", "Quantity",
        "ManaBox ID", "Scryfall ID", "Purchase price", "Misprint", "Altered", "Condition",
        "Language", "Purchase price currency",
    )

    /**
     * @param headerComment optional first line (e.g. source + date); written as a `//` comment the
     *   text importer skips. Ignored by the CSV formats.
     */
    fun format(
        entries: List<CollectionExportEntry>,
        format: CollectionFileFormat,
        headerComment: String? = null,
    ): String = when (format) {
        CollectionFileFormat.TEXT -> formatText(entries, headerComment)
        CollectionFileFormat.MOXFIELD_CSV -> formatMoxfieldCsv(entries)
        CollectionFileFormat.MANABOX_CSV -> formatManaBoxCsv(entries)
    }

    /** `manahub-<sourceKey>-YYYY-MM-DD.<ext>` in [timeZone]. */
    fun fileName(
        sourceKey: String,
        format: CollectionFileFormat,
        epochMillis: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): String {
        val date = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).date
        return "manahub-$sourceKey-$date.${format.fileExtension}"
    }

    /** Stable export order: name, then set, then collector number. */
    fun sorted(entries: List<CollectionExportEntry>): List<CollectionExportEntry> =
        entries.sortedWith(
            compareBy<CollectionExportEntry>({ it.name.lowercase() }, { it.setCode.lowercase() }, { it.collectorNumber })
        )

    private fun formatText(entries: List<CollectionExportEntry>, headerComment: String?): String = buildString {
        headerComment?.let { appendLine("// ${it.replace('\n', ' ')}") }
        entries.forEach { entry ->
            appendLine(
                DeckImportExportHelper.formatLine(
                    qty = entry.quantity,
                    name = entry.name,
                    setCode = entry.setCode,
                    collectorNumber = entry.collectorNumber,
                    includeSet = true,
                    isFoil = entry.isFoil,
                )
            )
        }
    }

    private fun formatMoxfieldCsv(entries: List<CollectionExportEntry>): String = buildString {
        appendLine(CsvCodec.row(MOXFIELD_HEADER))
        entries.forEach { entry ->
            appendLine(
                CsvCodec.row(
                    listOf(
                        entry.quantity.toString(),
                        "0",
                        entry.name,
                        entry.setCode.lowercase(),
                        CollectionCardAttributes.moxfieldCondition(entry.condition),
                        CollectionCardAttributes.moxfieldLanguage(entry.language),
                        if (entry.isFoil) "foil" else "",
                        "",
                        "",
                        entry.collectorNumber,
                        "False",
                        "False",
                        "",
                    )
                )
            )
        }
    }

    private fun formatManaBoxCsv(entries: List<CollectionExportEntry>): String = buildString {
        appendLine(CsvCodec.row(MANABOX_HEADER))
        entries.forEach { entry ->
            appendLine(
                CsvCodec.row(
                    listOf(
                        entry.name,
                        entry.setCode.uppercase(),
                        entry.setName,
                        entry.collectorNumber,
                        if (entry.isFoil) "foil" else "normal",
                        entry.rarity.lowercase(),
                        entry.quantity.toString(),
                        "",
                        entry.scryfallId,
                        "",
                        "false",
                        "false",
                        CollectionCardAttributes.manaBoxCondition(entry.condition),
                        entry.language.lowercase(),
                        "",
                    )
                )
            )
        }
    }
}
