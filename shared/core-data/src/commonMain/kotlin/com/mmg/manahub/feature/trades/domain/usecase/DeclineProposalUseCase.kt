package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.data.repository.TradesRepository

/** Declines a trade proposal by its [proposalId]. */
class DeclineProposalUseCase(private val repo: TradesRepository) {
    suspend operator fun invoke(proposalId: String) = repo.declineProposal(proposalId)
}
