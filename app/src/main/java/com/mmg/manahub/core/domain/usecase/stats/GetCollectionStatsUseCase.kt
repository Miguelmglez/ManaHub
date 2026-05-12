package com.mmg.manahub.core.domain.usecase.stats

import com.mmg.manahub.core.domain.model.CollectionStats
import com.mmg.manahub.core.domain.model.MtgColor
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCollectionStatsUseCase @Inject constructor(
    private val repository: StatsRepository
) {
    operator fun invoke(
        preferredCurrency: PreferredCurrency,
        colorFilter:       MtgColor? = null,
        setFilter:         String? = null
    ): Flow<CollectionStats> = 
        repository.observeCollectionStats(preferredCurrency, colorFilter, setFilter)
}
