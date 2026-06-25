package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import kotlinx.coroutines.flow.StateFlow

class GetSessionStateUseCase(
    private val repository: AuthRepository
) {
    operator fun invoke(): StateFlow<SessionState> = repository.sessionState
}
