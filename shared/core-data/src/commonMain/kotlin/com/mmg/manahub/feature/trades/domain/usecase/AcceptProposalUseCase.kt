package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.data.repository.TradesRepository

/** Accepts a trade proposal by its [proposalId]. */
class AcceptProposalUseCase(private val repo: TradesRepository) {
    suspend operator fun invoke(proposalId: String) = repo.acceptProposal(proposalId)
}
