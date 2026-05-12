package com.mmg.manahub.core.domain.usecase.stats

import com.mmg.manahub.core.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCollectionSetCodesUseCase @Inject constructor(
    private val repository: StatsRepository
) {
    operator fun invoke(): Flow<List<String>> = 
        repository.observeCollectionSetCodes()
}
