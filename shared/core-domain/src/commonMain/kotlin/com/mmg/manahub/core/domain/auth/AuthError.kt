package com.mmg.manahub.core.domain.auth

sealed class AuthError {
    data object InvalidCredentials : AuthError()
    /**
     * Returned when [AuthRepository.updatePassword] is called with a `currentPassword` that does
     * NOT match the account's actual current password. GoTrue's "Require current password when
     * updating" project setting (confirmed ON for this project) rejects the update with the SAME
     * `invalid_credentials` error code / HTTP 400 it uses for a failed sign-in — distinguished
     * here from the generic [InvalidCredentials] so the "Change password" screen can show
     * password-specific copy ("current password is incorrect") instead of the sign-in-flavored
     * "Incorrect email or password" message, which reads wrong in that context.
     */
    data object InvalidCurrentPassword : AuthError()
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
     * Returned when [AuthRepository.unlinkIdentity] is called on the account's LAST remaining
     * identity. GoTrue natively refuses to unlink a user's only identity (HTTP 422,
     * `single_identity_not_deletable`) — every account must keep at least one sign-in method. The
     * UI should surface a clear "you need another sign-in method first" message rather than the
     * generic [Unknown] fallback, even though the client-side disabled-button guard
     * (`identities.size <= 1`) should prevent this in the common case; this is the server-side
     * safety net for that guard.
     */
    data object SingleIdentityNotDeletable : AuthError()
    /**
     * Returned when the auth server rejects a request due to rate limiting (HTTP 429) — e.g. too
     * many resend-confirmation-email requests in a short window. The UI should ask the user to
     * wait before retrying rather than surfacing the generic [Unknown] message.
     */
    data object RateLimited : AuthError()
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
    /**
     * Returned by the "Set a password" flow of [AuthRepository.updatePassword]
     * (`currentPassword == null`) when the underlying Admin-API password write succeeded (HTTP 200)
     * but GoTrue revoked the account's active session server-side as a side effect of that write.
     *
     * Confirmed via production `auth_logs` (2026-08-17): the very next `GET /user` call after the
     * Admin API write returns HTTP 403 `session_not_found`, and every subsequent authenticated call
     * on that session degrades further to `bad_jwt "missing sub claim"` — including the user's next
     * "Change password" attempt, which used to surface as a confusing generic
     * [Unknown]("An unexpected error occurred"). The repository detects this by attempting a genuine
     * session refresh (`Auth.refreshCurrentSession()`, which exchanges the refresh token — distinct
     * from a `GET /user` resync) right after the write; when that ALSO fails, the refresh token is
     * revoked too and the session is unrecoverably dead, so the repository force-signs-out locally
     * and returns this instead. The password change itself genuinely succeeded — the UI must say
     * "please sign in again", never a generic failure message.
     */
    data object PasswordUpdatedSessionRevoked : AuthError()
    data class Unknown(val message: String?) : AuthError()
}
