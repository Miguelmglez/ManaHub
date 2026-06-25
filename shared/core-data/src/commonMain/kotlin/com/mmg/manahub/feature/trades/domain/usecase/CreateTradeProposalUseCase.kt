package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.data.remote.dto.TradeItemRequestDto
import com.mmg.manahub.core.data.repository.TradesRepository

/** Creates a new trade proposal to the given receiver with the specified items. */
class CreateTradeProposalUseCase(private val repo: TradesRepository) {
    suspend operator fun invoke(
        receiverId: String,
        items: List<TradeItemRequestDto>,
        includesReviewFromProposer: Boolean = false,
        includesReviewFromReceiver: Boolean = false,
        autoSend: Boolean = false,
    ) = repo.createProposal(receiverId, items, includesReviewFromProposer, includesReviewFromReceiver, autoSend)
}
