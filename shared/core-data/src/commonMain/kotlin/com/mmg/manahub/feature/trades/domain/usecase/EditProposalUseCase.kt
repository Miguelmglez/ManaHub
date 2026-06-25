package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.data.remote.dto.TradeItemRequestDto
import com.mmg.manahub.core.model.ReviewFlags
import com.mmg.manahub.core.data.repository.TradesRepository

/** Edits an existing trade proposal with new items and review flags. */
class EditProposalUseCase(private val repo: TradesRepository) {
    suspend operator fun invoke(
        proposalId: String,
        expectedVersion: Int,
        newItems: List<TradeItemRequestDto>,
        newReviewFlags: ReviewFlags,
    ) = repo.editProposal(proposalId, expectedVersion, newItems, newReviewFlags)
}
