package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.feature.trades.data.remote.dto.TradeItemRequestDto
import com.mmg.manahub.feature.trades.domain.repository.ReviewFlags
import com.mmg.manahub.feature.trades.domain.repository.TradesRepository
import javax.inject.Inject

class EditProposalUseCase @Inject constructor(private val repo: TradesRepository) {
    suspend operator fun invoke(
        proposalId: String,
        expectedVersion: Int,
        newItems: List<TradeItemRequestDto>,
        newReviewFlags: ReviewFlags,
    ) = repo.editProposal(proposalId, expectedVersion, newItems, newReviewFlags)
}
