package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.feature.trades.domain.repository.TradesRepository
import javax.inject.Inject

class RefreshTradesUseCase @Inject constructor(private val repo: TradesRepository) {
    suspend operator fun invoke(userId: String) = repo.refreshProposals(userId)
}
