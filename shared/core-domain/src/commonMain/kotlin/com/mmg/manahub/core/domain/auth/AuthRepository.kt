package com.mmg.manahub.core.domain.auth

import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val sessionState: StateFlow<SessionState>

    suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser>
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        nickname: String,
        avatarUrl: String?,
    ): AuthResult<AuthUser>

    /**
     * Signs in using a Google ID token obtained via Credential Manager.
     * @param idToken The Google ID token from [GoogleIdTokenCredential].
     * @param rawNonce The raw (unhashed) nonce used to generate [hashedNonce] for the request.
     */
    suspend fun signInWithGoogle(
        idToken: String,
        rawNonce: String
    ): AuthResult<AuthUser>

    /**
     * Signs up using a Google ID token obtained via Credential Manager.
     * @param idToken The Google ID token from [GoogleIdTokenCredential].
     * @param rawNonce The raw (unhashed) nonce used to generate [hashedNonce] for the request.
     * @param nickname The nickname entered by the user.
     * @param avatarUrl The avatar URL selected by the user.
     */
    suspend fun signUpWithGoogle(
        idToken: String,
        rawNonce: String,
        nickname: String,
        avatarUrl: String?
    ): AuthResult<AuthUser>

    suspend fun signOut(): AuthResult<Unit>
    suspend fun getCurrentUser(): AuthUser?
    suspend fun resetPassword(email: String): AuthResult<Unit>

    /**
     * Deletes the currently authenticated user account by calling a Supabase
     * PostgreSQL function with SECURITY DEFINER, then signs out locally.
     *
     * IMPORTANT: The following SQL function must exist in Supabase before calling this:
     * ```sql
     * CREATE OR REPLACE FUNCTION delete_current_user()
     * RETURNS void LANGUAGE plpgsql SECURITY DEFINER AS $$
     * BEGIN
     *   DELETE FROM auth.users WHERE id = auth.uid();
     * END;
     * $$;
     * ```
     */
    suspend fun deleteAccount(): AuthResult<Unit>

    /**
     * Updates the authenticated user's nickname in Supabase via the `update_user_nickname` RPC.
     * Returns the updated [AuthUser] on success.
     * May return [AuthError.NicknameInappropriate] if the server rejects the nickname.
     */
    suspend fun updateNickname(nickname: String): AuthResult<AuthUser>

    /**
     * Links a Google identity to an existing email/password account.
     *
     * Steps:
     * 1. Authenticates with email/password to obtain a valid session.
     * 2. Calls `signInWith(IDToken)` with the pending Google token. Because the session is
     *    active and the email matches, GoTrue links the Google identity to the existing user
     *    instead of creating a new account.
     *
     * @param email          The email address of the existing account.
     * @param password       The password entered by the user to confirm their identity.
     * @param pendingIdToken The Google ID token obtained during the original Google Sign-In attempt.
     * @param pendingNonce   The raw (unhashed) nonce from the original Google Sign-In request.
     *
     * @return [AuthResult.Success] on successful linking, or an [AuthResult.Error] carrying
     *   [AuthError.InvalidCredentials] if the password is wrong.
     */
    suspend fun linkGoogleIdentity(
        email: String,
        password: String,
        pendingIdToken: String,
        pendingNonce: String,
    ): AuthResult<AuthUser>

    /**
     * Updates the authenticated user's avatar URL in Supabase via the `update_user_avatar` RPC.
     * Pass null to remove the avatar.
     */
    suspend fun updateAvatarUrl(avatarUrl: String?): AuthResult<Unit>

    /**
     * Resends the sign-up confirmation email (GoTrue `OtpType.Email.SIGNUP`).
     *
     * A user pending confirmation (see [AuthError.EmailConfirmationRequired]/
     * [AuthError.EmailNotConfirmed]) has no active session yet, so — unlike every other member of
     * this interface — this call cannot resolve the target address from
     * `Auth.currentUserOrNull()`; [email] must be supplied by the caller (carried by the UI from
     * the original sign-up/sign-in attempt).
     *
     * @param email The address to resend the confirmation link to.
     */
    suspend fun resendConfirmationEmail(email: String): AuthResult<Unit>

    /**
     * Sends a reauthentication nonce to the current user's verified email via `Auth.reauthenticate`.
     * Required before a sensitive [updateEmail]/[updatePassword] call when server-side
     * reauthentication is enabled (Phase 2 configures/verifies this on the Supabase project).
     * The user enters the nonce they receive as the `code` parameter of the follow-up call.
     */
    suspend fun requestReauthentication(): AuthResult<Unit>

    /**
     * Changes the authenticated user's email address via `Auth.updateUser`.
     *
     * @param newEmail The new email address.
     * @param code The reauthentication nonce obtained via [requestReauthentication] and entered
     *   by the user. Never persisted to Room/DataStore — pass-through only.
     */
    suspend fun updateEmail(newEmail: String, code: String): AuthResult<Unit>

    /**
     * Changes the authenticated user's password via `Auth.updateUser`.
     *
     * @param newPassword The new password.
     * @param code The reauthentication nonce obtained via [requestReauthentication] and entered
     *   by the user. Never persisted to Room/DataStore — pass-through only.
     */
    suspend fun updatePassword(newPassword: String, code: String): AuthResult<Unit>

    /**
     * Unlinks an identity from the authenticated user's account via `Auth.unlinkIdentity`.
     *
     * @param identityId One of [AuthUser.identities]' [AuthIdentity.identityId] values.
     */
    suspend fun unlinkIdentity(identityId: String): AuthResult<Unit>

    /**
     * Links a Google identity to the authenticated user's account via the SDK's real
     * `Auth.linkIdentity` OAuth-redirect endpoint — distinct from [linkGoogleIdentity], which is a
     * same-email sign-in-then-link workaround built on the Credential-Manager ID-token flow.
     *
     * The plain `auth-kt` module does not launch a browser itself: it returns the authorization
     * URL the caller must open (e.g. via Custom Tabs). The OAuth-redirect callback is caught by
     * `supabaseClient.handleDeeplinks(intent)` (already wired in `MainActivity`), which completes
     * the link once the user finishes the flow in the browser.
     *
     * @param redirectUrl The `manahub://auth` deep-link the OAuth flow returns to.
     * @return The authorization URL to open, or null if the platform already launched it.
     */
    suspend fun linkGoogleIdentityNative(redirectUrl: String): AuthResult<String?>
}
