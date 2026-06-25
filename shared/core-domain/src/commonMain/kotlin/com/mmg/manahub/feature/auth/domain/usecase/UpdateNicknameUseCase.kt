package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.AuthRepository

/**
 * Updates the authenticated user's nickname in Supabase.
 * Delegates to [AuthRepository.updateNickname], which calls the `update_user_nickname` RPC.
 */
class UpdateNicknameUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(nickname: String): AuthResult<AuthUser> =
        repository.updateNickname(nickname)
}
