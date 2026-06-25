package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.data.repository.TradesRepository

/** Cancels a trade proposal by its [proposalId]. */
class CancelProposalUseCase(private val repo: TradesRepository) {
    suspend operator fun invoke(proposalId: String) = repo.cancelProposal(proposalId)
}
