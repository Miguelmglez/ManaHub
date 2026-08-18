package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.AuthRepository

/**
 * Changes the authenticated user's email address.
 * Delegates to [AuthRepository.updateEmail], which calls `Auth.updateUser`.
 *
 * NOTE: unreferenced by any current UI call site — [ConfirmEmailUpdateUseCase] backs the live
 * "Change email" path. See [AuthRepository.updateEmail]'s KDoc.
 *
 * @param code A reauthentication nonce entered by the user.
 */
class UpdateEmailUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(newEmail: String, code: String): AuthResult<Unit> =
        repository.updateEmail(newEmail, code)
}
