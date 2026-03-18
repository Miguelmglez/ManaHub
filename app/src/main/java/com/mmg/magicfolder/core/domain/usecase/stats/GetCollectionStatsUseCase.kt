package com.mmg.magicfolder.core.domain.usecase.stats

import com.mmg.magicfolder.core.domain.model.CollectionStats
import com.mmg.magicfolder.core.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCollectionStatsUseCase @Inject constructor(
    private val repository: StatsRepository
) {
    operator fun invoke(): Flow<CollectionStats> = repository.observeCollectionStats()
}
