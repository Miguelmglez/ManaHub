package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class GetSessionStateUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    operator fun invoke(): StateFlow<SessionState> = repository.sessionState
}
