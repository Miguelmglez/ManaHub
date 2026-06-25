package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.data.repository.TradesRepository

/** Marks a trade proposal as completed by its [proposalId]. */
class MarkCompletedUseCase(private val repo: TradesRepository) {
    suspend operator fun invoke(proposalId: String) = repo.markCompleted(proposalId)
}
