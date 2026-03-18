package com.mmg.magicfolder.core.domain.repository

import com.mmg.magicfolder.core.domain.model.CollectionStats
import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    fun observeCollectionStats(): Flow<CollectionStats>
}