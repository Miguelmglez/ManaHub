package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.data.remote.dto.TradeItemRequestDto
import com.mmg.manahub.core.model.ReviewFlags
import com.mmg.manahub.core.data.repository.TradesRepository

/** Creates a counter-proposal against an existing trade proposal. */
class CounterProposalUseCase(private val repo: TradesRepository) {
    suspend operator fun invoke(
        parentProposalId: String,
        items: List<TradeItemRequestDto>,
        reviewFlags: ReviewFlags
    ) = repo.counterProposal(parentProposalId, items, reviewFlags)
}
