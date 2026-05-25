package com.mmg.manahub.core.online.domain.usecase

import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import javax.inject.Inject

class UpdateLifeUseCase @Inject constructor(
    private val repository: OnlineSessionRepository,
) {
    // Sends optimistic broadcast immediately; RPC should be called after debounce.
    suspend fun broadcast(sessionId: String, slotIndex: Int, newLife: Int) =
        repository.broadcastLifeDelta(sessionId, slotIndex, newLife)

    suspend fun persist(sessionId: String, slotIndex: Int, newLife: Int): Result<Unit> =
        repository.updateLife(sessionId, slotIndex, newLife)
}
