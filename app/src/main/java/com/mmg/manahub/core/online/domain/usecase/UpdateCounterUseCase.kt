package com.mmg.manahub.core.online.domain.usecase

import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import javax.inject.Inject

class UpdateCounterUseCase @Inject constructor(
    private val repository: OnlineSessionRepository,
) {
    suspend fun broadcast(sessionId: String, slotIndex: Int, counterType: String, newValue: Int) =
        repository.broadcastCounterUpdate(sessionId, slotIndex, counterType, newValue)

    suspend operator fun invoke(
        sessionId: String,
        slotIndex: Int,
        counterType: String,
        delta: Int,
    ): Result<Unit> = repository.updateCounter(sessionId, slotIndex, counterType, delta)
}
