package com.mmg.manahub.feature.auth.data.repository

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.auth.data.remote.UserProfileDataSource
import com.mmg.manahub.feature.auth.domain.model.AuthError
import com.mmg.manahub.feature.auth.domain.model.AuthResult
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import io.github.jan.supabase.auth.user.UserInfo
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val supabaseAuth: Auth,
    private val userProfileDataSource: UserProfileDataSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AuthRepository {

    override val sessionState: Flow<SessionState> = supabaseAuth.sessionStatus
        .map { status -> status.toSessionState() }
        .flowOn(ioDispatcher)

    override suspend fun signInWithEmail(
        email: String,
        password: String
    ): AuthResult<AuthUser> = withContext(ioDispatcher) {
        runCatching {
            supabaseAuth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val user = supabaseAuth.currentUserOrNull()!!.toAuthUser()
            AuthResult.Success(user)
        }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String
    ): AuthResult<AuthUser> = withContext(ioDispatcher) {
        runCatching {
            supabaseAuth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            // currentUserOrNull() returns null when Supabase has email confirmation enabled
            // and the session is not yet active. In that case we return a specific error
            // so the ViewModel can show the email-confirmation flow instead of crashing.
            val userInfo = supabaseAuth.currentUserOrNull()
                ?: return@runCatching AuthResult.Error(AuthError.EmailConfirmationRequired)
            val user = userInfo.toAuthUser()
            userProfileDataSource.upsertUserProfile(user)
            AuthResult.Success(user)
        }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
    }

    override suspend fun signInWithGoogleIdToken(
        idToken: String,
        rawNonce: String
    ): AuthResult<AuthUser> = withContext(ioDispatcher) {
        runCatching {
            supabaseAuth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
                nonce = rawNonce
            }
            val user = supabaseAuth.currentUserOrNull()!!.toAuthUser()
            userProfileDataSource.upsertUserProfile(user)
            AuthResult.Success(user)
        }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
    }

    override suspend fun deleteAccount(): AuthResult<Unit> = withContext(ioDispatcher) {
        runCatching {
            // Calls the Supabase PostgreSQL function with SECURITY DEFINER.
            // SQL to run in Supabase SQL Editor before using this feature:
            // CREATE OR REPLACE FUNCTION delete_current_user()
            // RETURNS void LANGUAGE plpgsql SECURITY DEFINER AS $$
            // BEGIN
            //   DELETE FROM auth.users WHERE id = auth.uid();
            // END;
            // $$;
            supabaseClient.postgrest.rpc("delete_current_user")
            supabaseAuth.signOut()
            AuthResult.Success(Unit)
        }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
    }

    override suspend fun signOut(): AuthResult<Unit> = withContext(ioDispatcher) {
        runCatching {
            supabaseAuth.signOut()
            AuthResult.Success(Unit)
        }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
    }

    override suspend fun getCurrentUser(): AuthUser? =
        supabaseAuth.currentUserOrNull()?.toAuthUser()

    override suspend fun resetPassword(email: String): AuthResult<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                supabaseAuth.resetPasswordForEmail(email)
                AuthResult.Success(Unit)
            }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
        }

    // --- Mappers ---

    private fun UserInfo.toAuthUser() = AuthUser(
        id = id,
        email = email,
        displayName = userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
            ?: userMetadata?.get("name")?.jsonPrimitive?.contentOrNull,
        avatarUrl = userMetadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull,
        provider = identities?.firstOrNull()?.provider ?: "email"
    )

    private fun Throwable.toAuthError(): AuthError = when (this) {
        is RestException -> when (statusCode) {
            400  -> AuthError.InvalidCredentials
            422  -> AuthError.EmailAlreadyInUse
            404  -> AuthError.UserNotFound
            401  -> AuthError.SessionExpired
            else -> AuthError.Unknown(message)
        }
        is HttpRequestTimeoutException,
        is IOException -> AuthError.NetworkError
        else -> AuthError.Unknown(message)
    }

    private fun SessionStatus.toSessionState(): SessionState = when (this) {
        is SessionStatus.Authenticated -> SessionState.Authenticated(
            session.user!!.toAuthUser()
        )
        is SessionStatus.NotAuthenticated -> SessionState.Unauthenticated
        is SessionStatus.Initializing -> SessionState.Loading
        is SessionStatus.RefreshFailure -> SessionState.Unauthenticated
    }

}
