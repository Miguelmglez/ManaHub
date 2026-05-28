package com.mmg.manahub.core.online.domain.usecase

import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import javax.inject.Inject

class RevokeDefeatUseCase @Inject constructor(
    private val repository: OnlineSessionRepository,
) {
    suspend operator fun invoke(sessionId: String, slotIndex: Int): Result<Unit> =
        repository.revokeDefeat(sessionId, slotIndex)
}
