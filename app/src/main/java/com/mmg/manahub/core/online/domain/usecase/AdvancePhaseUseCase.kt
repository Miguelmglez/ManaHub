package com.mmg.manahub.core.online.domain.usecase

import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import javax.inject.Inject

class AdvancePhaseUseCase @Inject constructor(
    private val repository: OnlineSessionRepository,
) {
    suspend fun broadcast(sessionId: String, newPhase: String) =
        repository.broadcastPhaseChange(sessionId, newPhase)

    suspend fun persist(sessionId: String): Result<Unit> =
        repository.advancePhase(sessionId)
}
