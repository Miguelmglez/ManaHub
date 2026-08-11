package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.AuthRepository

/**
 * Sends a reauthentication nonce to the current user's verified email.
 * Delegates to [AuthRepository.requestReauthentication], which calls `Auth.reauthenticate`.
 * Required before a sensitive [UpdateEmailUseCase]/[UpdatePasswordUseCase] call when server-side
 * reauthentication is enabled.
 */
class RequestReauthenticationUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(): AuthResult<Unit> = repository.requestReauthentication()
}
