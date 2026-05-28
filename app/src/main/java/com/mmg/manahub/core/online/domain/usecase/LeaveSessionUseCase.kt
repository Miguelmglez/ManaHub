package com.mmg.manahub.core.online.domain.usecase

import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import javax.inject.Inject

class LeaveSessionUseCase @Inject constructor(
    private val repository: OnlineSessionRepository,
) {
    suspend operator fun invoke(sessionId: String): Result<Unit> {
        repository.disconnectRealtime(sessionId)
        return repository.leaveSession(sessionId)
    }
}
