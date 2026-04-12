package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.domain.model.CollectionStats
import com.mmg.manahub.core.domain.model.PreferredCurrency
import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    fun observeCollectionStats(preferredCurrency: PreferredCurrency): Flow<CollectionStats>
}