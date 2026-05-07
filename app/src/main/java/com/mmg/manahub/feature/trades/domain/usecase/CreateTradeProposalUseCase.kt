package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.feature.trades.data.remote.dto.TradeItemRequestDto
import com.mmg.manahub.feature.trades.domain.repository.TradesRepository
import javax.inject.Inject

class CreateTradeProposalUseCase @Inject constructor(private val repo: TradesRepository) {
    suspend operator fun invoke(
        receiverId: String,
        items: List<TradeItemRequestDto>,
        includesReviewFromProposer: Boolean = false,
        includesReviewFromReceiver: Boolean = false,
        autoSend: Boolean = false,
    ) = repo.createProposal(receiverId, items, includesReviewFromProposer, includesReviewFromReceiver, autoSend)
}
