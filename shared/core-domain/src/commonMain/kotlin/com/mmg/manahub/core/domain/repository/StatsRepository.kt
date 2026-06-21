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
}