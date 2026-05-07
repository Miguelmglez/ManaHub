package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.feature.trades.domain.repository.TradesRepository
import javax.inject.Inject

class GetActiveTradesUseCase @Inject constructor(private val repo: TradesRepository) {
    operator fun invoke() = repo.observeActiveProposals()
}
