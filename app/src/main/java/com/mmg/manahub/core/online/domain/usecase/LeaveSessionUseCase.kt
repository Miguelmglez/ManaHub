package com.mmg.manahub.core.online.domain.usecase

import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Leaves an online session: the RPC runs FIRST (and is retried once) so a transient failure does
 * not strand a ghost seat in the lobby, and the Realtime channel is released in a `finally` so the
 * local teardown happens even when the RPC ultimately fails.
 *
 * Returns the RPC's [Result] so the caller can tell the user their seat may still be listed.
 */
class LeaveSessionUseCase @Inject constructor(
    private val repository: OnlineSessionRepository,
) {
    suspend operator fun invoke(sessionId: String, guestToken: String? = null): Result<Unit> = try {
        var result = repository.leaveSession(sessionId, guestToken)
        if (result.isFailure) {
            delay(RETRY_DELAY_MS)
            result = repository.leaveSession(sessionId, guestToken)
        }
        result
    } finally {
        repository.disconnectRealtime(sessionId)
    }

    private companion object {
        const val RETRY_DELAY_MS = 1_000L
    }
}
