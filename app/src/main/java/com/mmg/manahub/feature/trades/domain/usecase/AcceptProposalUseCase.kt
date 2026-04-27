package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.feature.trades.domain.repository.TradesRepository
import javax.inject.Inject

class AcceptProposalUseCase @Inject constructor(private val repo: TradesRepository) {
    suspend operator fun invoke(proposalId: String) = repo.acceptProposal(proposalId)
}
