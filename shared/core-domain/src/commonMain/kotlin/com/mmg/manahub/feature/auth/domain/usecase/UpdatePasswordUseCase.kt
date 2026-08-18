package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.AuthRepository

/**
 * Changes the authenticated user's password.
 * Delegates to [AuthRepository.updatePassword], which calls `Auth.updateUser`.
 *
 * @param currentPassword The account's current password, required when the account already has
 *   a password ("Change password"); `null` for "Set a password" on an account with no password
 *   yet — see [AuthRepository.updatePassword]'s KDoc.
 */
class UpdatePasswordUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(newPassword: String, currentPassword: String?): AuthResult<Unit> =
        repository.updatePassword(newPassword, currentPassword)
}
