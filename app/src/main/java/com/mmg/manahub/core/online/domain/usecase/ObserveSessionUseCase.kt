package com.mmg.manahub.core.online.domain.usecase

import com.mmg.manahub.core.online.domain.model.SessionEvent
import com.mmg.manahub.core.online.domain.model.SessionSnapshot
import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSessionUseCase @Inject constructor(
    private val repository: OnlineSessionRepository,
) {
    // Returns full snapshot for reconnection. Remote snapshot always wins over local cache.
    suspend fun getSnapshot(sessionId: String): Result<SessionSnapshot> =
        repository.getSnapshot(sessionId)

    suspend fun connect(sessionId: String) = repository.connectRealtime(sessionId)

    suspend fun disconnect(sessionId: String) = repository.disconnectRealtime(sessionId)

    operator fun invoke(sessionId: String): Flow<SessionEvent> =
        repository.observeSession(sessionId)
}
