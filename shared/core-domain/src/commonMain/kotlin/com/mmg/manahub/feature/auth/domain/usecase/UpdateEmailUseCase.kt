package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.AuthRepository

/**
 * Changes the authenticated user's email address.
 * Delegates to [AuthRepository.updateEmail], which calls `Auth.updateUser`.
 *
 * @param code The reauthentication nonce obtained via [RequestReauthenticationUseCase].
 */
class UpdateEmailUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(newEmail: String, code: String): AuthResult<Unit> =
        repository.updateEmail(newEmail, code)
}
