package com.mmg.manahub.core.online.domain.usecase

import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import javax.inject.Inject

class AbandonMyActiveSessionUseCase @Inject constructor(
    private val repository: OnlineSessionRepository,
) {
    suspend operator fun invoke(sessionId: String, guestToken: String? = null): Result<Unit> =
        repository.abandonMyActiveSession(sessionId, guestToken)
}
