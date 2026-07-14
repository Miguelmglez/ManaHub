package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.data.repository.TradesRepository

/** Revokes the acceptance of a previously accepted trade proposal. */
class RevokeAcceptanceUseCase(private val repo: TradesRepository) {
    suspend operator fun invoke(proposalId: String) = repo.revokeAcceptance(proposalId)
}
