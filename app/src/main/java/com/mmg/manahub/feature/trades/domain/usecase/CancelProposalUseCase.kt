package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.feature.trades.domain.repository.TradesRepository
import javax.inject.Inject

class CancelProposalUseCase @Inject constructor(private val repo: TradesRepository) {
    suspend operator fun invoke(proposalId: String) = repo.cancelProposal(proposalId)
}
