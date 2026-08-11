package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.AuthRepository

/**
 * Resends the sign-up confirmation email to [email].
 * Delegates to [AuthRepository.resendConfirmationEmail], which calls the GoTrue
 * `OtpType.Email.SIGNUP` resend endpoint.
 */
class ResendConfirmationEmailUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(email: String): AuthResult<Unit> =
        repository.resendConfirmationEmail(email)
}
