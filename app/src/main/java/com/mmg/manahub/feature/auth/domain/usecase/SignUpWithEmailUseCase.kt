package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.feature.auth.domain.model.AuthResult
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

class SignUpWithEmailUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    /**
     * Registers a new user with email and password.
     * The [nickname] is stored in Supabase user metadata after sign-up.
     */
    suspend operator fun invoke(
        email: String,
        password: String,
        nickname: String,
    ): AuthResult<AuthUser> = repository.signUpWithEmail(email.trim(), password, nickname.trim())
}
