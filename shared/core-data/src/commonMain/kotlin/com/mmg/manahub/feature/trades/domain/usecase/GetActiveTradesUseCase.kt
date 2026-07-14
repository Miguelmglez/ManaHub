package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.data.repository.TradesRepository

/** Observes the list of currently active trade proposals. */
class GetActiveTradesUseCase(private val repo: TradesRepository) {
    operator fun invoke() = repo.observeActiveProposals()
}
