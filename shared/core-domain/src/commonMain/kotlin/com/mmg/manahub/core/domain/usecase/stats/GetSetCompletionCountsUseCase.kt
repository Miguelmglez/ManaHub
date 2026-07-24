package com.mmg.manahub.core.domain.usecase.stats

import com.mmg.manahub.core.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow

/** Distinct owned card count per set code, for the Stats "Set completion" section. */
class GetSetCompletionCountsUseCase(
    private val repository: StatsRepository
) {
    operator fun invoke(): Flow<Map<String, Int>> =
        repository.observeDistinctOwnedCountBySet()
}
