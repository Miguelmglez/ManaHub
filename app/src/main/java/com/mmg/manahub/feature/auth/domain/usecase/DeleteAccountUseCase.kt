package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.feature.auth.domain.model.AuthResult
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Permanently deletes the currently authenticated user account from Supabase
 * and signs out locally. This action is irreversible.
 *
 * Requires the `delete_current_user` PostgreSQL function to be deployed in Supabase
 * (see [AuthRepository.deleteAccount] for the required SQL).
 */
class DeleteAccountUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(): AuthResult<Unit> = repository.deleteAccount()
}
