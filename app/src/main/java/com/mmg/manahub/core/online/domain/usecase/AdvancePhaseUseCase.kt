package com.mmg.manahub.core.online.domain.usecase

import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import javax.inject.Inject

class AdvancePhaseUseCase @Inject constructor(
    private val repository: OnlineSessionRepository,
) {
    suspend fun broadcast(sessionId: String, newPhase: String, activePlayerSlot: Int, turnNumber: Int) =
        repository.broadcastPhaseChange(sessionId, newPhase, activePlayerSlot, turnNumber)

    suspend fun persist(sessionId: String): Result<Unit> =
        repository.advancePhase(sessionId)
}
