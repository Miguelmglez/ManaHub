package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.domain.model.CollectionStats
import com.mmg.manahub.core.domain.model.MtgColor
import com.mmg.manahub.core.domain.model.PreferredCurrency
import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    fun observeCollectionStats(
        preferredCurrency: PreferredCurrency,
        colorFilter:       MtgColor? = null,
        setFilter:         String? = null
    ): Flow<CollectionStats>

    fun observeCollectionSetCodes(): Flow<List<String>>
}