package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.feature.auth.domain.model.AuthResult
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Signs the user up using a Google ID token obtained from Credential Manager.
 *
 * @param idToken   The Google ID token from [GoogleIdTokenCredential].
 * @param rawNonce  The raw (unhashed) nonce that was hashed and included in the Credential request.
 * @param nickname  The nickname entered by the user.
 * @param avatarUrl The avatar URL selected by the user.
 */
class SignUpWithGoogleUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(
        idToken: String,
        rawNonce: String,
        nickname: String,
        avatarUrl: String?,
    ): AuthResult<AuthUser> =
        repository.signUpWithGoogle(idToken, rawNonce, nickname, avatarUrl)
}
