package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.feature.auth.domain.model.AuthResult
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Links a Google identity to an existing email/password ManaHub account.
 *
 * The user first authenticates with their email/password (step 1), then the
 * pending Google ID token is submitted to GoTrue which links the Google provider
 * to the existing account (step 2). On success the user is signed in and the
 * Google identity is permanently associated so future Google Sign-In attempts
 * will succeed without a conflict.
 *
 * @param email          The email of the existing account.
 * @param password       The password the user entered in the linking dialog.
 * @param pendingIdToken The Google ID token stored in [AuthUiState.GoogleEmailConflictLinking].
 * @param pendingNonce   The raw nonce stored in [AuthUiState.GoogleEmailConflictLinking].
 */
class LinkGoogleIdentityUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(
        email: String,
        password: String,
        pendingIdToken: String,
        pendingNonce: String,
    ): AuthResult<AuthUser> =
        repository.linkGoogleIdentity(email, password, pendingIdToken, pendingNonce)
}
