package com.mmg.manahub.core.online.domain.usecase

import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import javax.inject.Inject

class ConfirmDefeatUseCase @Inject constructor(
    private val repository: OnlineSessionRepository,
) {
    suspend operator fun invoke(sessionId: String, slotIndex: Int, guestToken: String? = null): Result<Unit> =
        repository.confirmDefeat(sessionId, slotIndex, guestToken)

    suspend fun broadcast(sessionId: String, slotIndex: Int) =
        repository.broadcastDefeatConfirmed(sessionId, slotIndex)
}
