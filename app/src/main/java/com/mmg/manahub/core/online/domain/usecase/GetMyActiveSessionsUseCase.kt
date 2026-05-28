package com.mmg.manahub.core.online.domain.usecase

import com.mmg.manahub.core.online.domain.model.ActiveSession
import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Returns all active sessions owned by the current user.
 *
 * Delegates directly to [OnlineSessionRepository.getMyActiveSessions] with no
 * additional transformation, keeping the use case single-responsibility.
 */
@Singleton
class GetMyActiveSessionsUseCase @Inject constructor(
    private val repository: OnlineSessionRepository,
) {
    suspend operator fun invoke(): Result<List<ActiveSession>> =
        repository.getMyActiveSessions()
}
