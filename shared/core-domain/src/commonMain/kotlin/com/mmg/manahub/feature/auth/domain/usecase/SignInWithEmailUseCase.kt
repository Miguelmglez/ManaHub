package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.AuthRepository

class SignInWithEmailUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): AuthResult<AuthUser> =
        repository.signInWithEmail(email.trim(), password)
}
