package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.data.repository.TradesRepository

/** Observes the historical list of completed/cancelled trade proposals. */
class GetTradeHistoryUseCase(private val repo: TradesRepository) {
    operator fun invoke() = repo.observeProposalHistory()
}
