package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.feature.trades.data.remote.dto.TradeItemRequestDto
import com.mmg.manahub.feature.trades.domain.repository.TradesRepository
import javax.inject.Inject

class CounterProposalUseCase @Inject constructor(private val repo: TradesRepository) {
    suspend operator fun invoke(parentProposalId: String, items: List<TradeItemRequestDto>) =
        repo.counterProposal(parentProposalId, items)
}
