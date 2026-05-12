package com.mmg.manahub.feature.auth.domain.repository

import com.mmg.manahub.feature.auth.domain.model.AuthResult
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.model.SessionState
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
}
