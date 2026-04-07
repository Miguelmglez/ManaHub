package com.mmg.magicfolder.core.domain.repository

import com.mmg.magicfolder.core.domain.model.CollectionStats
import com.mmg.magicfolder.core.domain.model.PreferredCurrency
import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    fun observeCollectionStats(preferredCurrency: PreferredCurrency): Flow<CollectionStats>
}