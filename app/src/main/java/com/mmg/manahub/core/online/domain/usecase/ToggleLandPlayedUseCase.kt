package com.mmg.manahub.core.online.domain.usecase

import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import javax.inject.Inject

class ToggleLandPlayedUseCase @Inject constructor(
    private val repository: OnlineSessionRepository,
) {
    suspend fun broadcast(sessionId: String, slotIndex: Int, played: Boolean) =
        repository.broadcastLandToggled(sessionId, slotIndex, played)
}
