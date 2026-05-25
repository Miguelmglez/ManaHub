package com.mmg.manahub.core.online.domain.usecase

import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import javax.inject.Inject

class UpdateCounterUseCase @Inject constructor(
    private val repository: OnlineSessionRepository,
) {
    suspend operator fun invoke(
        sessionId: String,
        slotIndex: Int,
        counterType: String,
        delta: Int,
    ): Result<Unit> = repository.updateCounter(sessionId, slotIndex, counterType, delta)
}
