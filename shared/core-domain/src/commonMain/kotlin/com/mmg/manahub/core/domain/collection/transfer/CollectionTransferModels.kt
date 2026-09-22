package com.mmg.manahub.core.domain.collection.transfer

/** File formats the collection importer reads and the exporter writes. */
enum class CollectionFileFormat(val fileExtension: String, val mimeType: String) {
    /** Moxfield / MTG Arena `qty name (SET) number *F*` lines. */
    TEXT("txt", "text/plain"),

    /** Moxfield collection CSV export. */
    MOXFIELD_CSV("csv", "text/csv"),

    /** ManaBox collection CSV export (carries the Scryfall id). */
    MANABOX_CSV("csv", "text/csv"),
}

/**
 * One parsed import line, before Scryfall resolution. At least one of [scryfallId], [name] or
 * ([setCode] + [collectorNumber]) is present.
 *
 * @property rawLine the source line as the user wrote it, shown back when it cannot be resolved.
 */
data class CollectionImportLine(
    val quantity: Int,
    val name: String?,
    val setCode: String?,
    val collectorNumber: String?,
    val scryfallId: String?,
    val isFoil: Boolean,
    val condition: String,
    val language: String,
    val rawLine: String,
)

/**
 * Result of [CollectionImportParser.parse].
 *
 * @property rejectedLines non-blank lines that are neither a card, a header nor a comment.
 */
data class ParsedCollectionImport(
    val format: CollectionFileFormat,
    val lines: List<CollectionImportLine>,
    val rejectedLines: List<String>,
) {
    /** Total card copies across [lines]. */
    val totalCopies: Int get() = lines.sumOf { it.quantity }
}

/** One per-printing collection row to export. */
data class CollectionExportEntry(
    val quantity: Int,
    val name: String,
    val setCode: String,
    val setName: String,
    val collectorNumber: String,
    val scryfallId: String,
    val rarity: String,
    val isFoil: Boolean,
    val condition: String,
    val language: String,
)
