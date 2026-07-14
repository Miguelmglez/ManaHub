package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.data.repository.TradesRepository

/** Refreshes all trade proposals for the given user from the remote backend. */
class RefreshTradesUseCase(private val repo: TradesRepository) {
    suspend operator fun invoke(userId: String) = repo.refreshProposals(userId)
}
