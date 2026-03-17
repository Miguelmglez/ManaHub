package com.mmg.magicfolder.code.core.domain.repository

import com.mmg.magicfolder.code.core.domain.model.CollectionStats
import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    fun observeCollectionStats(): Flow<CollectionStats>
}