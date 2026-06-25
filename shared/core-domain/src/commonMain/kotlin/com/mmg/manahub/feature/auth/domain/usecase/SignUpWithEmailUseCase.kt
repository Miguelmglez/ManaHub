package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.AuthRepository

class SignUpWithEmailUseCase(
    private val repository: AuthRepository
) {
    /**
     * Registers a new user with email and password.
     * The [nickname] is stored in Supabase user metadata after sign-up.
     * The optional [avatarUrl] is persisted in `user_profiles` if provided.
     */
    suspend operator fun invoke(
        email: String,
        password: String,
        nickname: String,
        avatarUrl: String? = null,
    ): AuthResult<AuthUser> = repository.signUpWithEmail(
        email = email.trim(),
        password = password,
        nickname = nickname.trim(),
        avatarUrl = avatarUrl,
    )
}
