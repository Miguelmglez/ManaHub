package com.mmg.manahub.core.domain.collection.transfer

import com.mmg.manahub.feature.decks.domain.engine.DeckImportExportHelper
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Serialises [CollectionExportEntry] rows into the three [CollectionFileFormat]s.
 *
 * Round-trip fidelity is NOT equal across formats:
 * - the two CSVs parse back through [CollectionImportParser] to the same entries (Moxfield's
 *   condition scale is coarser than the app's, see [CollectionCardAttributes.moxfieldCondition]);
 * - [CollectionFileFormat.TEXT] carries quantity, name, printing and foil only. Condition and
 *   language are not part of the format, so re-importing a text export rebuilds every row at
 *   [CollectionCardAttributes.DEFAULT_CONDITION]/[CollectionCardAttributes.DEFAULT_LANGUAGE] — a
 *   backup-and-restore through TEXT both loses grading and splits rows that differed only by it.
 *   CSV is the lossless choice; see also [countLoosePrintings] for the rows TEXT cannot even pin
 *   to a printing.
 */
object CollectionExportFormatter {

    /** Escapes one bounded CSV record without retaining the complete output. */
    fun csvRecord(fields: List<String>): String = CsvCodec.row(fields)

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
    ): String = buildString {
        append(header(format,headerComment))
        entries.forEach { append(record(it,format)) }
    }

    /** The local date an export is stamped with, for both the file name and the header comment. */
    fun exportDate(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
        Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).date

    /** `manahub-<sourceKey>-YYYY-MM-DD.<ext>` in [timeZone]. */
    fun fileName(
        sourceKey: String,
        format: CollectionFileFormat,
        epochMillis: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): String = "manahub-$sourceKey-${exportDate(epochMillis, timeZone)}.${format.fileExtension}"

    /**
     * Rows [format] writes without a printing, so a re-import resolves them to an arbitrary one.
     * Only [CollectionFileFormat.TEXT] can lose a printing: `DeckImportExportHelper.formatLine`
     * drops the whole `(SET) number` group when either half is missing, because the importer's
     * regex only accepts a set code followed by a collector number — emitting `(SET)` alone would
     * make the name itself unparseable. The rows are still exported, never dropped; the count is
     * reported so the loss is visible.
     */
    fun countLoosePrintings(entries: List<CollectionExportEntry>, format: CollectionFileFormat): Int =
        if (format != CollectionFileFormat.TEXT) 0
        else entries.count { it.setCode.isBlank() || it.collectorNumber.isBlank() }

    /** Stable export order: name, then set, then collector number. */
    fun sorted(entries: List<CollectionExportEntry>): List<CollectionExportEntry> =
        entries.sortedWith(
            compareBy<CollectionExportEntry>({ it.name.lowercase() }, { it.setCode.lowercase() }, { it.collectorNumber })
        )

    /** Header emitted once by bounded export writers. */
    fun header(format: CollectionFileFormat,comment: String?=null): String=when(format) {
        CollectionFileFormat.TEXT -> comment?.let { "// ${it.replace('\n',' ')}\n" }.orEmpty()
        CollectionFileFormat.MOXFIELD_CSV -> CsvCodec.row(MOXFIELD_HEADER)+"\n"
        CollectionFileFormat.MANABOX_CSV -> CsvCodec.row(MANABOX_HEADER)+"\n"
    }
    /** One source row, retaining every represented attribute and the original card name. */
    fun record(entry: CollectionExportEntry,format: CollectionFileFormat): String=when(format) {
        CollectionFileFormat.TEXT -> DeckImportExportHelper.formatLine(entry.quantity,entry.name,entry.setCode,entry.collectorNumber,true,entry.isFoil)+"\n"
        CollectionFileFormat.MOXFIELD_CSV -> CsvCodec.row(listOf(entry.quantity.toString(),"0",entry.name,entry.setCode.lowercase(),CollectionCardAttributes.moxfieldCondition(entry.condition),CollectionCardAttributes.moxfieldLanguage(entry.language),if(entry.isFoil)"foil" else "","","",entry.collectorNumber,"False","False",""))+"\n"
        CollectionFileFormat.MANABOX_CSV -> CsvCodec.row(listOf(entry.name,entry.setCode.uppercase(),entry.setName,entry.collectorNumber,if(entry.isFoil)"foil" else "normal",entry.rarity.lowercase(),entry.quantity.toString(),"",entry.scryfallId,"","false","false",CollectionCardAttributes.manaBoxCondition(entry.condition),entry.language.lowercase(),""))+"\n"
    }
}
