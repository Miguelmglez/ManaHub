package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.AuthRepository

/**
 * Confirms a "forgot password" reset from the email deep link, WITHOUT a reauthentication code.
 * Delegates to [AuthRepository.confirmPasswordReset], which calls `Auth.updateUser` against the
 * temporary recovery session GoTrue establishes when the user taps the recovery link — distinct
 * from [UpdatePasswordUseCase], which requires a [RequestReauthenticationUseCase] nonce.
 */
class ConfirmPasswordResetUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(newPassword: String): AuthResult<Unit> =
        repository.confirmPasswordReset(newPassword)
}
