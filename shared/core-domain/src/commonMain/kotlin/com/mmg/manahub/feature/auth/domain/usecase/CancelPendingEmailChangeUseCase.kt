package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.AuthRepository

/**
 * Cancels a pending "Change email" request for the authenticated user.
 * Delegates to [AuthRepository.cancelPendingEmailChange], which calls the
 * `cancel_pending_email_change` RPC.
 */
class CancelPendingEmailChangeUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(): AuthResult<Unit> =
        repository.cancelPendingEmailChange()
}
