package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.CollectionStats
import com.mmg.manahub.core.model.MtgColor
import com.mmg.manahub.core.model.PreferredCurrency
import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    fun observeCollectionStats(
        preferredCurrency: PreferredCurrency,
        colorFilter:       MtgColor? = null,
        setFilter:         String? = null
    ): Flow<CollectionStats>

    fun observeCollectionSetCodes(): Flow<List<String>>

    /**
     * Distinct owned card count per set code, keyed by [com.mmg.manahub.core.model.MagicSet.code].
     * Global/unfiltered — ignores the active color/set filters (set completion is inherently
     * per-set); the caller joins this against Scryfall set metadata (card_count) to compute ratios.
     */
    fun observeDistinctOwnedCountBySet(): Flow<Map<String, Int>>
}