package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.data.repository.TradesRepository
import javax.inject.Inject

class GetTradeThreadUseCase @Inject constructor(private val repo: TradesRepository) {
    operator fun invoke(rootProposalId: String) = repo.observeProposalThread(rootProposalId)
}
