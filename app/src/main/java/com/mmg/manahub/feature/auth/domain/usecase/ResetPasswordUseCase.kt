package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.feature.auth.domain.model.AuthResult
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Use case that triggers a password reset email for the given address.
 *
 * Calls [AuthRepository.resetPassword] and returns the raw [AuthResult].
 * The caller is responsible for mapping the result to UI state.
 */
class ResetPasswordUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(email: String): AuthResult<Unit> =
        repository.resetPassword(email.trim())
}
