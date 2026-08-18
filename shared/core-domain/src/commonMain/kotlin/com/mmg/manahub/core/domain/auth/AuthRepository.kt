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
     * Changes the authenticated user's email address via `Auth.updateUser`.
     *
     * NOTE: unreferenced by any current UI call site — [confirmEmailUpdate] is the live "Change
     * email" path (see its KDoc). Left as-is; not touched by the password reauth-gate removal
     * that retired the sibling `requestReauthentication`-based flow for passwords.
     *
     * @param newEmail The new email address.
     * @param code A reauthentication nonce entered by the user. Never persisted to
     *   Room/DataStore — pass-through only.
     */
    suspend fun updateEmail(newEmail: String, code: String): AuthResult<Unit>

    /**
     * Changes the authenticated user's password.
     *
     * Architecture pivot (replaces the retired email-nonce reauthentication flow — GoTrue never
     * validated the `nonce` field on `updateUser` calls on this project, which was the actual
     * root cause of the "any code is accepted" bug). This project's Supabase dashboard has
     * "Require current password when updating" ON, which GoTrue enforces server-side on every
     * `Auth.updateUser` password change for an account that already HAS a password.
     *
     * [currentPassword] non-null ("Change password" flow, account already has an `email`
     * identity) goes through the self-service `Auth.updateUser { password = ...; currentPassword
     * = ... }` path; a mismatch surfaces as [AuthError.InvalidCurrentPassword].
     *
     * [currentPassword] `null` ("Set a password" flow) does NOT simply skip the current-password
     * check on `Auth.updateUser` — an account signed up via Google already has a password set
     * server-side by the `set-google-account-password` Edge Function (fired on every Google
     * signup) even though it has no `email` identity, so GoTrue's "Require current password"
     * gate legitimately rejects a null-current-password `Auth.updateUser` call for it. This case
     * is instead routed through the `set-account-password` Edge Function, which uses the Admin
     * API (`adminClient.auth.admin.updateUserById`) — an operation with no `current_password`
     * gate at all — so it works uniformly whether the account secretly already has a password or
     * genuinely has none.
     *
     * @param newPassword The new password.
     * @param currentPassword The account's current password, required for the "Change password"
     *   flow (an account that already has an `email` identity). Pass `null` for the "Set a
     *   password" flow (an account with no `email` identity yet, e.g. a Google-only account) —
     *   see above for why that is NOT simply "skip the check" but a different call path entirely.
     *   Never persisted to Room/DataStore — pass-through only.
     */
    suspend fun updatePassword(newPassword: String, currentPassword: String?): AuthResult<Unit>

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

    /**
     * Confirms a password change started via the "forgot password" email link, WITHOUT a
     * reauthentication [code] — distinct from [updatePassword].
     *
     * Tapping the recovery link (`Auth.resetPasswordForEmail`) establishes a temporary,
     * fully-authenticated recovery session on-device (GoTrue mints a real access token for it), so
     * no nonce/reauthentication step is needed here. This calls `Auth.updateUser` with only the
     * new password set.
     *
     * SECURITY (fixed — see `feedback_password_recovery_amr_gate` in
     * `.claude/agent-memory/android-kotlin-architect/`): merely reaching this method proves
     * NOTHING about how the current session was established, because `MainActivity`'s
     * `manahub://auth?type=recovery` deep-link routing is driven by an attacker-controllable URI —
     * any installed app can fire that intent at an already-authenticated user and land here via a
     * forged "recovery" callback. The caller (`AuthViewModel.confirmPasswordReset`) MUST verify
     * `AuthUser.isRecoverySession` on the CURRENT session before invoking this method; a session
     * without that flag must never reach this call. Do not remove that gate under the assumption
     * that reaching this screen/method is itself proof of a genuine recovery flow — it is not.
     *
     * @param newPassword The new password, already validated by the caller.
     */
    suspend fun confirmPasswordReset(newPassword: String): AuthResult<Unit>

    /**
     * Changes the authenticated user's email address WITHOUT a reauthentication [code] — distinct
     * from [updateEmail].
     *
     * This is safe because the Supabase project has "Secure email change" enabled: GoTrue sends
     * confirmation links to BOTH the old and the new email address, and the change only takes
     * effect once both are confirmed. That double-confirmation already fully protects this path
     * against an unauthorized change, which makes the reauthentication-code gate ahead of
     * [updateEmail] redundant friction for email specifically — unlike [updatePassword], which has
     * no equivalent server-side double-confirm and must keep the gate. This calls `Auth.updateUser`
     * with only the new email set — no `nonce`.
     *
     * @param newEmail The new email address, already validated by the caller.
     */
    suspend fun confirmEmailUpdate(newEmail: String): AuthResult<Unit>

    /**
     * Cancels a pending "Change email" request via the `cancel_pending_email_change` RPC.
     *
     * Until now there was no way to abort an in-flight [confirmEmailUpdate]/[updateEmail]
     * confirmation once started (Supabase's "Secure email change" double-confirms via links to
     * both the old and new inbox, and the pending change otherwise just sits there until
     * confirmed or it expires). The RPC clears `auth.users.email_change` server-side for the
     * calling user and is a verified no-op (no error, no side effect) when nothing is pending —
     * safe to call unconditionally without a client-side "is something pending" guard, though
     * gating the UI affordance on [AuthUser.newEmail] being non-null is still correct UX.
     *
     * The RPC returns `void` — it does not push an updated `newEmail` back through
     * [sessionState] (GoTrue does not proactively re-sync `auth.users` changes made outside its
     * own `updateUser` flow into the client's in-memory session). Callers should clear their own
     * local "pending" UI state on success rather than waiting for [sessionState] to reflect it.
     */
    suspend fun cancelPendingEmailChange(): AuthResult<Unit>
}
