package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.CollectionStats
import com.mmg.manahub.core.model.CollectionSummary
import com.mmg.manahub.core.model.MtgColor
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.toSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface StatsRepository {
    fun observeCollectionStats(
        preferredCurrency: PreferredCurrency,
        colorFilter:       MtgColor? = null,
        setFilter:         String? = null
    ): Flow<CollectionStats>

    /**
     * Unfiltered totals, value and color/rarity counts only. Implementations should back this with
     * a handful of cheap queries instead of the full [observeCollectionStats] pipeline; the default
     * derives it from that pipeline so every implementation stays correct.
     */
    fun observeCollectionSummary(): Flow<CollectionSummary> =
        observeCollectionStats(PreferredCurrency.USD).map { it.toSummary() }

    fun observeCollectionSetCodes(): Flow<List<String>>

    /**
     * Distinct owned card count per set code, keyed by [com.mmg.manahub.core.model.MagicSet.code].
     * Global/unfiltered — ignores the active color/set filters (set completion is inherently
     * per-set); the caller joins this against Scryfall set metadata (card_count) to compute ratios.
     */
    fun observeDistinctOwnedCountBySet(): Flow<Map<String, Int>>
}