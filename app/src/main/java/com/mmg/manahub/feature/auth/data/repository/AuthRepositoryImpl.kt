package com.mmg.manahub.feature.auth.data.repository

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.auth.data.remote.UpdateAvatarUrlDto
import com.mmg.manahub.feature.auth.data.remote.UpdateNicknameDto
import com.mmg.manahub.feature.auth.data.remote.UserProfileDataSource
import com.mmg.manahub.feature.auth.data.remote.SupabaseUserProfileService
import com.mmg.manahub.feature.auth.domain.model.AuthError
import com.mmg.manahub.feature.auth.domain.model.AuthResult
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val supabaseAuth: Auth,
    private val userProfileDataSource: UserProfileDataSource,
    private val supabaseUserProfileService: SupabaseUserProfileService,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    /**
     * Session state flow enriched with `user_profiles` data.
     *
     * When the status transitions to [SessionStatus.Authenticated]:
     * 1. Emits the fast [SessionState.Authenticated] immediately (from auth metadata only),
     *    so the UI renders without waiting for the DB fetch.
     * 2. Then fetches the full profile from `user_profiles` and emits an enriched
     *    [SessionState.Authenticated] with nickname/gameTag/avatarUrl from the DB.
     *
     * Exceptions from the profile fetch are caught silently — the first emit is always
     * delivered so the session is never blocked by a network failure.
     */
    @Suppress("OPT_IN_USAGE")
    override val sessionState: Flow<SessionState> = supabaseAuth.sessionStatus
        .map { status -> status.toSessionState() }
        .flatMapLatest { state ->
            if (state !is SessionState.Authenticated) {
                flowOf(state)
            } else {
                flow {
                    // Fast emit: available immediately from in-memory session metadata.
                    emit(state)
                    // Enriched emit: fetch server-side profile (nickname, gameTag, avatarUrl).
                    try {
                        val profile = userProfileDataSource.fetchUserProfile(state.user.id)
                        if (profile != null) {
                            val enrichedUser = state.user.copy(
                                nickname = profile.nickname ?: state.user.nickname,
                                gameTag = profile.gameTag ?: state.user.gameTag,
                                avatarUrl = profile.avatarUrl ?: state.user.avatarUrl,
                            )
                            syncToDataStore(enrichedUser)
                            emit(SessionState.Authenticated(enrichedUser))
                        } else {
                            // No profile row yet (e.g. email-confirmed user whose profile row
                            // hasn't been created by the trigger yet). Create it now.
                            val profileUser = userProfileDataSource.upsertUserProfile(state.user)
                            syncToDataStore(profileUser)
                            emit(SessionState.Authenticated(profileUser))
                        }
                    } catch (_: Exception) {
                        // Non-fatal: session is valid even if profile enrichment fails.
                    }
                }
            }
        }
        .flowOn(ioDispatcher)

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AuthResult<AuthUser> = withContext(ioDispatcher) {
        runCatching {
            supabaseAuth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val userInfo = supabaseAuth.currentUserOrNull()
                ?: return@runCatching AuthResult.Error(AuthError.SessionExpired)

            var user = userInfo.toAuthUser()

            // Enrich with server-side profile data (nickname, gameTag, avatarUrl).
            val profile = userProfileDataSource.fetchUserProfile(user.id)
            if (profile != null) {
                user = user.copy(
                    nickname = profile.nickname ?: user.nickname,
                    gameTag = profile.gameTag ?: user.gameTag,
                    avatarUrl = profile.avatarUrl ?: user.avatarUrl,
                )
            }

            // Sync auth user to local DataStore so ProfileViewModel reflects latest data.
            syncToDataStore(user)

            AuthResult.Success(user)
        }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        nickname: String,
        avatarUrl: String?,
    ): AuthResult<AuthUser> = withContext(ioDispatcher) {
        runCatching {
            supabaseAuth.signUpWith(Email) {
                this.email = email
                this.password = password
                // Store nickname and avatarUrl in auth metadata so the handle_new_user
                // trigger can populate user_profiles even before email confirmation.
                this.data = buildJsonObject {
                    put("nickname", nickname)
                    if (avatarUrl != null) put("avatar_url", avatarUrl)
                }
            }
            // currentUserOrNull() returns null when Supabase has email confirmation enabled
            // and the session is not yet active. In that case we return a specific error
            // so the ViewModel can show the email-confirmation flow instead of crashing.
            val userInfo = supabaseAuth.currentUserOrNull()
                ?: return@runCatching AuthResult.Error(AuthError.EmailConfirmationRequired)

            // Attempt to persist the nickname. Non-fatal: if it fails, proceed with sign-up.
            val nicknameResult = updateNicknameInternal(nickname)
            var user = when (nicknameResult) {
                is AuthResult.Success -> nicknameResult.data
                is AuthResult.Error -> userInfo.toAuthUser().copy(nickname = nickname.ifBlank { null })
            }

            // Apply avatarUrl from sign-up flow if provided (e.g. pre-filled from Google profile).
            if (avatarUrl != null) {
                user = user.copy(avatarUrl = avatarUrl)
            }

            val profileUser = userProfileDataSource.upsertUserProfile(user)

            // Sync to local DataStore so ProfileViewModel reflects latest data.
            syncToDataStore(profileUser)

            AuthResult.Success(profileUser)
        }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
    }

    override suspend fun signInWithGoogleIdToken(
        idToken: String,
        rawNonce: String,
    ): AuthResult<AuthUser> = withContext(ioDispatcher) {
        runCatching {
            supabaseAuth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
                nonce = rawNonce
            }
            val userInfo = supabaseAuth.currentUserOrNull()
                ?: return@runCatching AuthResult.Error(AuthError.SessionExpired)
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

            // Sync to local DataStore so ProfileViewModel reflects latest data.
            syncToDataStore(profileUser)

            AuthResult.Success(profileUser)
        }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
    }

    override suspend fun updateNickname(nickname: String): AuthResult<AuthUser> =
        withContext(ioDispatcher) {
            updateNicknameInternal(nickname)
        }

    // --- Private helpers ---

    /**
     * Calls the `update_user_nickname` Supabase RPC via Retrofit, then re-fetches
     * the current user.
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
            val response: Response<Unit> = supabaseUserProfileService.updateNickname(
                UpdateNicknameDto(newNickname = nickname.trim())
            )
            if (!response.isSuccessful) {
                return if (response.code() == 400) {
                    AuthResult.Error(AuthError.NicknameInappropriate)
                } else {
                    AuthResult.Error(AuthError.Unknown("HTTP ${response.code()}"))
                }
            }
            val user = supabaseAuth.currentUserOrNull()
                ?: return AuthResult.Error(AuthError.SessionExpired)
            AuthResult.Success(user.toAuthUser().copy(nickname = nickname.trim()))
        }.getOrElse { e ->
            // Non-2xx from Retrofit throws HttpException; map it like the old RestException path.
            if (e is retrofit2.HttpException && e.code() == 400) {
                AuthResult.Error(AuthError.NicknameInappropriate)
            } else {
                AuthResult.Error(e.toAuthError())
            }
        }
    }

    override suspend fun updateAvatarUrl(avatarUrl: String?): AuthResult<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val response: Response<Unit> = supabaseUserProfileService.updateAvatarUrl(
                    UpdateAvatarUrlDto(newAvatarUrl = avatarUrl)
                )
                if (!response.isSuccessful) {
                    return@runCatching AuthResult.Error(
                        AuthError.Unknown("HTTP ${response.code()}")
                    )
                }
                AuthResult.Success(Unit)
            }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
        }

    override suspend fun deleteAccount(): AuthResult<Unit> = withContext(ioDispatcher) {
        runCatching {
            val response: Response<Unit> = supabaseUserProfileService.deleteCurrentUser()
            if (!response.isSuccessful) {
                return@runCatching AuthResult.Error(
                    AuthError.Unknown("HTTP ${response.code()}")
                )
            }
            // signOut may fail if the session is already invalidated server-side after deletion.
            // The account is gone regardless — always return Success once the RPC call succeeds.
            runCatching { supabaseAuth.signOut() }
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

    /**
     * Syncs nickname and avatarUrl to local [UserPreferencesDataStore] after a
     * successful sign-in, sign-up, or Google sign-in.
     * This ensures [ProfileViewModel.uiState.playerName] and [avatarUrl] are updated
     * from the server data without requiring the user to manually refresh.
     */
    private suspend fun syncToDataStore(user: AuthUser) {
        try {
            user.nickname?.takeIf { it.isNotBlank() }?.let {
                userPreferencesDataStore.savePlayerName(it)
            }
            user.avatarUrl?.let {
                userPreferencesDataStore.saveAvatarUrl(it)
            }
        } catch (_: Exception) {
            // Non-fatal: DataStore write failures must not surface to the user.
        }
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
        provider = identities?.firstOrNull()?.provider ?: "email",
    )

    private fun Throwable.toAuthError(): AuthError = when (this) {
        is RestException -> when (statusCode) {
            400  -> AuthError.InvalidCredentials
            422  -> AuthError.EmailAlreadyInUse
            404  -> AuthError.UserNotFound
            401  -> AuthError.SessionExpired
            else -> AuthError.Unknown(message)
        }
        is retrofit2.HttpException -> when (code()) {
            400  -> AuthError.InvalidCredentials
            422  -> AuthError.EmailAlreadyInUse
            404  -> AuthError.UserNotFound
            401  -> AuthError.SessionExpired
            else -> AuthError.Unknown(message())
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
        is SessionStatus.Authenticated -> session.user
            ?.let { SessionState.Authenticated(it.toAuthUser()) }
            ?: SessionState.Unauthenticated
        is SessionStatus.NotAuthenticated -> SessionState.Unauthenticated
        is SessionStatus.Initializing -> SessionState.Loading
        is SessionStatus.RefreshFailure -> SessionState.Unauthenticated
    }
}
