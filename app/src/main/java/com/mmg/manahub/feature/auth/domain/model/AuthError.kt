package com.mmg.manahub.feature.auth.domain.model

sealed class AuthError {
    data object InvalidCredentials : AuthError()
    data object EmailAlreadyInUse : AuthError()
    data object NetworkError : AuthError()
    data object UserNotFound : AuthError()
    data object SessionExpired : AuthError()
    /** Returned when Supabase requires email confirmation before the session is active. */
    data object EmailConfirmationRequired : AuthError()
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
