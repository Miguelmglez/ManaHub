package com.mmg.manahub.core.data.local.entity.projection

data class TotalsProjection(val totalCards: Int, val uniqueCards: Int)
data class CardValueProjection(
    val scryfallId:    String,
    val name:          String,
    val priceUsd:      Double,
    val priceEur:      Double,
    val isFoil:        Boolean,
    val imageArtCrop:  String?,
    val colorIdentity: String = "",
    val setCode:       String = "",
    val setName:       String = "",
    val rarity:        String = "",
    /** Only selected by queries that need it (most-valuable list, artist gallery) — null otherwise. */
    val imageNormal:   String? = null,
)

data class ArtistCountProjection(val artist: String?, val count: Int)
data class SetValueProjection(val setCode: String, val totalValue: Double)
data class TagProjection(val tags: String?)
data class ColorCountProjection(val colorIdentity: String, val count: Int)
data class RarityCountProjection(val rarity: String, val count: Int)
data class TypeCountProjection(val typeLine: String, val count: Int)
data class CmcCountProjection(val cmc: Int, val count: Int)
data class SetCountProjection(val setCode: String, val count: Int)

// --- Phase 2 (2026-07 stats expansion) ---

/** One distinct owned card's price in both currencies, for avg/median card value. */
data class UniqueCardPriceProjection(
    val scryfallId: String,
    val priceUsd:   Double,
    val priceEur:   Double,
)

/** The card with the highest summed owned quantity (across its collection rows). */
data class DuplicateCardProjection(
    val scryfallId:    String,
    val name:          String,
    val imageArtCrop:  String?,
    val isFoil:        Boolean,
    val colorIdentity: String,
    val setCode:       String,
    val setName:       String,
    val rarity:        String,
    val priceUsd:      Double,
    val priceEur:      Double,
    val totalQuantity: Int,
)

/** Distinct owned card counts per major format, for the format-coverage chip row. */
data class FormatCoverageProjection(
    val commanderCount: Int,
    val modernCount:    Int,
    val standardCount:  Int,
)

/** Raw `keywords` JSON column from one owned collection entry. */
data class KeywordsProjection(val keywords: String?)

// --- Hall of Fame enrichment (2026-07 stats expansion) ---

/**
 * The card (by `oracle_id`) owned in the most distinct (set_code, lang) printings. Unlike
 * [DuplicateCardProjection] (a 1:1 GROUP BY on `scryfall_id`), this groups MULTIPLE rows per
 * `oracle_id` — the non-aggregate columns are made deterministic via SQLite's documented
 * single-min/max-aggregate rule: with exactly one `MAX()` in the query, every bare column is
 * drawn from the row that produced that max, so [releasedAt] pins the representative row to the
 * newest owned printing. [releasedAt] itself is unused by the domain mapper — it exists purely to
 * anchor that determinism.
 */
data class VariantCardProjection(
    val scryfallId:    String,
    val name:          String,
    val imageArtCrop:  String?,
    val isFoil:        Boolean,
    val colorIdentity: String,
    val setCode:       String,
    val setName:       String,
    val rarity:        String,
    val priceUsd:      Double,
    val priceEur:      Double,
    val variantCount:  Int,
    val releasedAt:    String? = null,
)

/** One release-decade bucket's total owned quantity (e.g. decade = 1990 for the 1990s). */
data class DecadeCountProjection(val decade: Int, val count: Int)