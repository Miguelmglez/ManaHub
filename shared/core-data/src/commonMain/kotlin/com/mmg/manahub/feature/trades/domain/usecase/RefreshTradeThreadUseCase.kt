package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.data.repository.TradesRepository

/** Refreshes the negotiation thread for a root proposal from the remote backend. */
class RefreshTradeThreadUseCase(private val repo: TradesRepository) {
    suspend operator fun invoke(rootProposalId: String, userId: String) =
        repo.refreshProposalThread(rootProposalId, userId)
}
