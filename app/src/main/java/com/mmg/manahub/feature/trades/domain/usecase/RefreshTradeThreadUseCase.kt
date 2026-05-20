package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.feature.trades.domain.repository.TradesRepository
import javax.inject.Inject

class RefreshTradeThreadUseCase @Inject constructor(private val repo: TradesRepository) {
    suspend operator fun invoke(rootProposalId: String, userId: String) =
        repo.refreshProposalThread(rootProposalId, userId)
}
