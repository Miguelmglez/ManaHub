package core.domain.repository

import core.domain.model.CollectionStats
import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    fun observeCollectionStats(): Flow<CollectionStats>
}