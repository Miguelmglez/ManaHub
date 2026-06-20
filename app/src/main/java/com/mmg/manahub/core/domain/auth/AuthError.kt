package com.mmg.manahub.core.domain.auth

sealed class AuthError {
    data object InvalidCredentials : AuthError()
    data object EmailAlreadyInUse : AuthError()
    data object NetworkError : AuthError()
    data object UserNotFound : AuthError()
    data object SessionExpired : AuthError()
    /** Returned when Supabase requires email confirmation before the session is active. */
    data object EmailConfirmationRequired : AuthError()
    /**
     * Returned when a sign-in attempt is rejected because the account's email has not yet
     * been confirmed (GoTrue `email_not_confirmed`).
     *
     * Distinct from [InvalidCredentials]: the credentials are correct, but confirmation is
     * still pending. With device-independent server-side confirmation (ADR-003) the user may
     * reach the password screen before clicking the confirmation link, so this must surface a
     * "confirm your email first" message rather than the misleading "wrong password" one.
     */
    data object EmailNotConfirmed : AuthError()
    /** Returned when the Supabase RPC rejects the nickname due to inappropriate content (HTTP 400). */
    data object NicknameInappropriate : AuthError()
    /** Returned when the supplied nickname exceeds the 30-character limit. */
    data object NicknameTooLong : AuthError()
    /**
     * Returned when a Google Sign-In attempt is made with an email that already exists
     * as an email/password account.
     *
     * The pending Google credentials are stored here so the user can enter their password
     * to link the Google identity to the existing account without re-launching the Google
     * account picker.
     *
     * @param email       The email extracted from the Google ID token JWT payload.
     * @param pendingIdToken The Google ID token that triggered the 422 response.
     * @param pendingNonce   The raw (unhashed) nonce used for the original Google Sign-In request.
     */
    data class GoogleEmailConflict(
        val email: String,
        val pendingIdToken: String,
        val pendingNonce: String,
    ) : AuthError()
    /**
     * Returned when a Google Sign-In succeeds at the OAuth level but no ManaHub profile
     * exists for this Google account. The user must create an account first.
     * The [email] field carries the Google email so the sign-up form can be pre-filled.
     */
    data class NoProfileFound(val email: String?) : AuthError()
    data class Unknown(val message: String?) : AuthError()
}
