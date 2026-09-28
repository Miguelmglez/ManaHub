package com.mmg.manahub.core.model

/**
 * The lightweight collection headline for dashboards: totals, value in both currencies and the
 * color/rarity breakdowns, without the full [CollectionStats] pipeline (tags, keywords, curves...).
 */
data class CollectionSummary(
    val totalCards: Int,
    val uniqueCards: Int,
    val totalValueUsd: Double,
    val totalValueEur: Double,
    val byColor: Map<MtgColor, Int>,
    val byRarity: Map<Rarity, Int>,
)

/** Projects the full stats down to the dashboard [CollectionSummary]. */
fun CollectionStats.toSummary(): CollectionSummary = CollectionSummary(
    totalCards = totalCards,
    uniqueCards = uniqueCards,
    totalValueUsd = totalValueUsd,
    totalValueEur = totalValueEur,
    byColor = byColor,
    byRarity = byRarity,
)
