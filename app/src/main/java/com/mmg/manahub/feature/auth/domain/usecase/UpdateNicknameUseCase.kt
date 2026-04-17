package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.feature.auth.domain.model.AuthResult
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Updates the authenticated user's nickname in Supabase.
 * Delegates to [AuthRepository.updateNickname], which calls the `update_user_nickname` RPC.
 */
class UpdateNicknameUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(nickname: String): AuthResult<AuthUser> =
        repository.updateNickname(nickname)
}
