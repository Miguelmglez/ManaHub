package com.mmg.manahub.feature.auth.domain.repository

import com.mmg.manahub.feature.auth.domain.model.AuthResult
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.model.SessionState
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val sessionState: Flow<SessionState>

    suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser>
    suspend fun signUpWithEmail(email: String, password: String): AuthResult<AuthUser>

    /**
     * Signs in using a Google ID token obtained via Credential Manager.
     * @param idToken The Google ID token from [GoogleIdTokenCredential].
     * @param rawNonce The raw (unhashed) nonce used to generate [hashedNonce] for the request.
     */
    suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String): AuthResult<AuthUser>

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
}
