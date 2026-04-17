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
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
        password: String,
        nickname: String,
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

            // Attempt to persist the nickname. Non-fatal: if it fails, proceed with sign-up.
            val nicknameResult = updateNicknameInternal(nickname)
            val user = when (nicknameResult) {
                is AuthResult.Success -> nicknameResult.data
                is AuthResult.Error -> userInfo.toAuthUser().copy(nickname = nickname.ifBlank { null })
            }

            val profileUser = userProfileDataSource.upsertUserProfile(user)
            AuthResult.Success(profileUser)
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
            val userInfo = supabaseAuth.currentUserOrNull()!!
            var user = userInfo.toAuthUser()

            // If Google user has no nickname yet, auto-generate one from their Google display name,
            // truncated to NICKNAME_MAX_LENGTH chars. This avoids the nickname-selection prompt.
            if (user.nickname == null) {
                val googleName = userInfo.userMetadata
                    ?.get("full_name")?.jsonPrimitive?.contentOrNull
                    ?: userInfo.userMetadata?.get("name")?.jsonPrimitive?.contentOrNull
                if (googleName != null) {
                    val autoNickname = googleName.take(NICKNAME_MAX_LENGTH)
                    val nicknameResult = updateNicknameInternal(autoNickname)
                    user = when (nicknameResult) {
                        is AuthResult.Success -> nicknameResult.data
                        is AuthResult.Error -> user.copy(nickname = autoNickname)
                    }
                }
            }

            val profileUser = userProfileDataSource.upsertUserProfile(user)
            AuthResult.Success(profileUser)
        }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
    }

    override suspend fun updateNickname(nickname: String): AuthResult<AuthUser> =
        withContext(ioDispatcher) {
            updateNicknameInternal(nickname)
        }

    // --- Private helpers ---

    /**
     * Calls the `update_user_nickname` Supabase RPC, then re-fetches the current user.
     * Must be called from within a [withContext] block using [ioDispatcher].
     *
     * Maps HTTP 400 specifically to [AuthError.NicknameInappropriate] since the RPC returns
     * 400 when the nickname contains inappropriate content.
     */
    private suspend fun updateNicknameInternal(nickname: String): AuthResult<AuthUser> {
        if (nickname.length > NICKNAME_MAX_LENGTH) {
            return AuthResult.Error(AuthError.NicknameTooLong)
        }
        return runCatching {
            // Uses a parameterized RPC call — the nickname is passed as a named
            // argument in the JSON body, NOT concatenated into any SQL string.
            supabaseClient.postgrest.rpc(
                "update_user_nickname",
                mapOf("new_nickname" to nickname.trim())
            )
            val user = supabaseAuth.currentUserOrNull()
                ?: return AuthResult.Error(AuthError.SessionExpired)
            AuthResult.Success(user.toAuthUser().copy(nickname = nickname.trim()))
        }.getOrElse { e ->
            // HTTP 400 from the profanity/constraint trigger → NicknameInappropriate.
            // The server's raw error message is intentionally not surfaced to the UI.
            if (e is RestException && e.statusCode == 400) {
                AuthResult.Error(AuthError.NicknameInappropriate)
            } else {
                AuthResult.Error(e.toAuthError())
            }
        }
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

    /**
     * Maps a Supabase [UserInfo] to the domain [AuthUser].
     * Reads [AuthUser.nickname] from `userMetadata["nickname"]` with fallback to email prefix.
     * Reads [AuthUser.gameTag] from `userMetadata["game_tag"]`.
     */
    private fun UserInfo.toAuthUser() = AuthUser(
        id = id,
        email = email,
        nickname = userMetadata?.get("nickname")?.jsonPrimitive?.contentOrNull
            ?: email?.substringBefore('@'),
        gameTag = userMetadata?.get("game_tag")?.jsonPrimitive?.contentOrNull,
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

    companion object {
        /** Must match the CHECK constraint on the `user_profiles.nickname` column in Supabase. */
        private const val NICKNAME_MAX_LENGTH = 30
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
