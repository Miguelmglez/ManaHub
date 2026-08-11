package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.AuthRepository

/**
 * Links a Google identity to the authenticated user's account via the SDK's real OAuth-redirect
 * `Auth.linkIdentity` endpoint. Delegates to [AuthRepository.linkGoogleIdentityNative] — distinct
 * from [LinkGoogleIdentityUseCase], which is a same-email sign-in-then-link workaround built on the
 * Credential-Manager ID-token flow.
 *
 * @param redirectUrl The `manahub://auth` deep-link the OAuth flow returns to.
 * @return On success, the authorization URL the caller must open (e.g. via Custom Tabs), or null
 *   if the platform already launched it.
 */
class LinkGoogleIdentityNativeUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(redirectUrl: String): AuthResult<String?> =
        repository.linkGoogleIdentityNative(redirectUrl)
}
