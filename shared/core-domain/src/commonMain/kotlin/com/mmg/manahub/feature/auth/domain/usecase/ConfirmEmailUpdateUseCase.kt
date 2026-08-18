package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.AuthRepository

/**
 * Changes the authenticated user's email address WITHOUT a reauthentication code — distinct from
 * [UpdateEmailUseCase]. Delegates to [AuthRepository.confirmEmailUpdate], which relies on
 * Supabase's "Secure email change" double-confirmation (links sent to both the old and new inbox)
 * instead of a pre-change reauthentication-code gate.
 */
class ConfirmEmailUpdateUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(newEmail: String): AuthResult<Unit> =
        repository.confirmEmailUpdate(newEmail)
}
