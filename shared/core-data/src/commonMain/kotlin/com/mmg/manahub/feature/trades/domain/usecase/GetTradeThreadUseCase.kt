package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.data.repository.TradesRepository

/** Observes the negotiation thread for a given root proposal. */
class GetTradeThreadUseCase(private val repo: TradesRepository) {
    operator fun invoke(rootProposalId: String) = repo.observeProposalThread(rootProposalId)
}
