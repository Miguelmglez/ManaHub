package com.mmg.manahub.core.model

data class CollectionStats(
    val totalCards:        Int,
    val uniqueCards:       Int,
    val totalDecks:        Int,
    val totalValueUsd:     Double,
    val totalValueEur:     Double,
    val mostValuableCards: List<CardValue>,
    val byColor:           Map<MtgColor, Int>,
    val byRarity:          Map<Rarity, Int>,
    val byType:            Map<CardType, Int>,
    val cmcDistribution:   Map<Int, Int>,
    val bySet:             Map<String, Int>,
    // Innovative Stats
    val totalFoil:         Int     = 0,
    val totalFullArt:      Int     = 0,
    val topArtist:         String? = null,
    val topArtistCount:    Int     = 0,
    val avgManaValue:      Double  = 0.0,
    val avgPower:          Double? = null,
    val avgToughness:      Double? = null,
    val oldestCard:        CardValue? = null,
    val newestCard:        CardValue? = null,
    // Set Stats
    val topSetByCount:     Pair<String, Int>?    = null,
    val topSetByValue:     Pair<String, Double>? = null,
    // AutoTags Stats
    val autoTagDistribution: Map<String, Int> = emptyMap(),

    // Phase 2 (2026-07 stats expansion) — all computed within the same filtered pipeline.
    /** Fraction [0f, 1f] of total collection value (active currency) held by the top 10 cards. */
    val valueConcentrationTop10Percent: Float = 0f,
    /** Mean price (active currency) across distinct owned cards with price > 0. */
    val avgCardValue: Double = 0.0,
    /** Median price (active currency) across distinct owned cards with price > 0. */
    val medianCardValue: Double = 0.0,
    /** Fraction [0f, 1f] of total collection value (active currency) contributed by foil copies. */
    val foilValueSharePercent: Float = 0f,
    /** The single card with the highest summed owned quantity across all its collection rows. */
    val mostDuplicatedCard: CardValue? = null,
    val mostDuplicatedCardCount: Int = 0,
    /** Distinct owned cards legal per format, keyed by display name ("Commander", "Modern", "Standard"). */
    val formatCoverage: Map<String, Int> = emptyMap(),
    /** Top-10 keywords by owned collection-entry count. */
    val keywordDistribution: Map<String, Int> = emptyMap(),

    // Hall of Fame enrichment (2026-07 stats expansion)
    /** The single card owned in the most DISTINCT (set_code, lang) printings — a different axis
     * than [mostDuplicatedCard] (which is highest summed quantity of ONE exact printing). */
    val mostVariantsCard: CardValue? = null,
    val mostVariantsCount: Int = 0,
    /** Up to 15 owned cards illustrated by [topArtist], for the Top Artist gallery row. */
    val topArtistCards: List<CardValue> = emptyList(),
    /** Owned card quantity bucketed by release decade ("1990s", "2000s", ...), sorted ascending
     * by the caller. Replaces the retired collection-growth-by-month chart. */
    val decadeDistribution: Map<String, Int> = emptyMap(),
)

data class CardValue(
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
    /** Full card image (non-cropped). Only populated by queries that select it (e.g. most-valuable, artist gallery). */
    val imageNormal:   String? = null,
)

enum class MtgColor { W, U, B, R, G, COLORLESS }
enum class Rarity   { COMMON, UNCOMMON, RARE, MYTHIC, SPECIAL }
enum class CardType { CREATURE, INSTANT, SORCERY, ENCHANTMENT, ARTIFACT, PLANESWALKER, LAND, BATTLE, OTHER }
