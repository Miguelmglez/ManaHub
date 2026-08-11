package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.AuthRepository

/**
 * Changes the authenticated user's password.
 * Delegates to [AuthRepository.updatePassword], which calls `Auth.updateUser`.
 *
 * @param code The reauthentication nonce obtained via [RequestReauthenticationUseCase].
 */
class UpdatePasswordUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(newPassword: String, code: String): AuthResult<Unit> =
        repository.updatePassword(newPassword, code)
}
