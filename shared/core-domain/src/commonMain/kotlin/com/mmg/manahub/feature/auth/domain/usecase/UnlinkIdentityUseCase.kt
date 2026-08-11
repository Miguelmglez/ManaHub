package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.AuthRepository

/**
 * Unlinks an identity from the authenticated user's account.
 * Delegates to [AuthRepository.unlinkIdentity], which calls `Auth.unlinkIdentity`.
 *
 * @param identityId One of [com.mmg.manahub.core.domain.auth.AuthUser.identities]'
 *   [com.mmg.manahub.core.domain.auth.AuthIdentity.identityId] values.
 */
class UnlinkIdentityUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(identityId: String): AuthResult<Unit> =
        repository.unlinkIdentity(identityId)
}
