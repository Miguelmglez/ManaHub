package com.mmg.manahub.feature.auth.data.repository

import android.util.Log
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.di.ApplicationScope
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Response
import java.io.IOException
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val supabaseAuth: Auth,
    private val userProfileDataSource: UserProfileDataSource,
    private val supabaseUserProfileService: SupabaseUserProfileService,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    @Named("supabase") private val supabaseOkHttpClient: OkHttpClient,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    private val profileRefreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Session state flow enriched with `user_profiles` data.
     * Shared across all collectors via `stateIn` to avoid redundant DB calls.
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
    override val sessionState: StateFlow<SessionState> = combine(
        supabaseAuth.sessionStatus,
        profileRefreshSignal.onStart { emit(Unit) }
    ) { status, _ -> status }
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
                                avatarUrl = profile.avatarUrl,
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
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionState.Loading
        )

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

            val user = mapUserInfoToAuthUser(userInfo)
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
                // The handle_new_user trigger reads raw_user_meta_data->>'nickname' and
                // inserts it into user_profiles automatically — no extra RPC needed.
                this.data = buildJsonObject {
                    put("nickname", nickname)
                    if (avatarUrl != null) put("avatar_url", avatarUrl)
                }
            }
            // currentUserOrNull() returns null when email confirmation is enabled.
            val userInfo = supabaseAuth.currentUserOrNull()
                ?: return@runCatching AuthResult.Error(AuthError.EmailConfirmationRequired)

            val user = mapUserInfoToAuthUser(userInfo).copy(
                nickname = nickname.ifBlank { null },
                avatarUrl = avatarUrl ?: mapUserInfoToAuthUser(userInfo).avatarUrl,
            )

            val profileUser = userProfileDataSource.upsertUserProfile(user)

            syncToDataStore(profileUser)

            AuthResult.Success(profileUser)
        }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
    }

    override suspend fun signInWithGoogle(
        idToken: String,
        rawNonce: String
    ): AuthResult<AuthUser> = withContext(ioDispatcher) {
        runCatching {
            val localNickname = userPreferencesDataStore.playerNameFlow.first()
            val localAvatar = userPreferencesDataStore.avatarUrlFlow.first()

            supabaseAuth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
                nonce = rawNonce
                // Pass local profile metadata so handle_new_user trigger can pick it up
                // if this is a first-time Google sign-in.
                this.data = buildJsonObject {
                    if (localNickname.isNotBlank()) put("nickname", localNickname)
                    if (localAvatar != null) put("avatar_url", localAvatar)
                }
            }
            val userInfo = supabaseAuth.currentUserOrNull()
                ?: return@runCatching AuthResult.Error(AuthError.SessionExpired)

            val existingProfile = userProfileDataSource.fetchUserProfile(userInfo.id)

            val finalUser = if (existingProfile == null) {
                // New Google user (but via Sign In): metadata push above handled the trigger,
                // but we also upsert here to be 100% sure we get a game_tag back.
                val localNickname = userPreferencesDataStore.playerNameFlow.first()
                val localAvatar = userPreferencesDataStore.avatarUrlFlow.first()

                val newUser = mapUserInfoToAuthUser(userInfo).copy(
                    nickname = localNickname.takeIf { it.isNotBlank() },
                    avatarUrl = localAvatar
                )
                val profileUser = userProfileDataSource.upsertUserProfile(newUser)
                callSetGoogleAccountPasswordEdgeFunction()
                profileUser
            } else {
                // Returning Google user: use the profile data already on the server.
                mapUserInfoToAuthUser(userInfo).copy(
                    nickname = existingProfile.nickname ?: mapUserInfoToAuthUser(userInfo).nickname,
                    gameTag = existingProfile.gameTag,
                    avatarUrl = existingProfile.avatarUrl,
                )
            }

            syncToDataStore(finalUser)
            profileRefreshSignal.tryEmit(Unit)

            AuthResult.Success(finalUser)
        }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
    }

    override suspend fun signUpWithGoogle(
        idToken: String,
        rawNonce: String,
        nickname: String,
        avatarUrl: String?
    ): AuthResult<AuthUser> = withContext(ioDispatcher) {
        runCatching {
            supabaseAuth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
                nonce = rawNonce
                // Pass nickname in metadata so the handle_new_user trigger can pick it up
                // for new accounts.
                this.data = buildJsonObject {
                    put("nickname", nickname)
                    if (avatarUrl != null) put("avatar_url", avatarUrl)
                }
            }
            val userInfo = supabaseAuth.currentUserOrNull()
                ?: return@runCatching AuthResult.Error(AuthError.SessionExpired)

            // For sign-up, we ALWAYS upsert the profile to ensure the provided
            // nickname and avatarUrl are used, even if the account already existed.
            val user = mapUserInfoToAuthUser(userInfo).copy(
                nickname = nickname.ifBlank { null },
                avatarUrl = avatarUrl,
            )
            val profileUser = userProfileDataSource.upsertUserProfile(user)
            
            // If it was truly new, this will set the password. If not, it might
            // be redundant but harmless depending on the Edge Function logic.
            callSetGoogleAccountPasswordEdgeFunction()

            syncToDataStore(profileUser)
            profileRefreshSignal.tryEmit(Unit)

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

            val updatedUser = mapUserInfoToAuthUser(user).copy(nickname = nickname.trim())
            syncToDataStore(updatedUser)
            profileRefreshSignal.tryEmit(Unit)

            AuthResult.Success(updatedUser)
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

                supabaseAuth.currentUserOrNull()?.let { mapUserInfoToAuthUser(it) }?.copy(avatarUrl = avatarUrl)?.let {
                    syncToDataStore(it)
                }
                profileRefreshSignal.tryEmit(Unit)

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
        supabaseAuth.currentUserOrNull()?.let { mapUserInfoToAuthUser(it) }

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
            // Only update DataStore if the server actually has values. 
            // This prevents overwriting a valid local nickname/avatar with null 
            // during the very first authenticated emit (before profile enrichment).
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

    /**
     * Calls the `set-google-account-password` Edge Function to assign a strong random
     * password to the newly created Google account. This allows the user to also sign in
     * via email/password if needed, and triggers a welcome email from Supabase.
     *
     * This is fire-and-forget: failures are logged but never surfaced to the caller.
     * The supabaseOkHttpClient already injects apikey + Authorization headers automatically.
     */
    private fun callSetGoogleAccountPasswordEdgeFunction() {
        try {
            val password = generateSecurePassword()
            val body = JSONObject().put("password", password).toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${BuildConfig.SUPABASE_URL}/functions/v1/set-google-account-password")
                .post(body)
                .build()
            supabaseOkHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && BuildConfig.DEBUG) {
                    Log.w(TAG, "set-google-account-password returned HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "set-google-account-password call failed", e)
            }
        }
    }

    /**
     * Generates a cryptographically secure 16-character password guaranteed to contain
     * at least one uppercase letter, one lowercase letter, one digit, and one symbol.
     */
    private fun generateSecurePassword(): String {
        val uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lowercase = "abcdefghijklmnopqrstuvwxyz"
        val digits = "0123456789"
        val symbols = "!@#\$%^&*()"
        val all = uppercase + lowercase + digits + symbols
        val rng = SecureRandom()

        val mandatory = listOf(
            uppercase[rng.nextInt(uppercase.length)],
            lowercase[rng.nextInt(lowercase.length)],
            digits[rng.nextInt(digits.length)],
            symbols[rng.nextInt(symbols.length)],
        )
        val rest = (0 until 12).map { all[rng.nextInt(all.length)] }

        return (mandatory + rest).shuffled(rng).joinToString("")
    }

    // --- Mappers ---

    /**
     * Maps a Supabase [UserInfo] to the domain [AuthUser].
     * Reads [AuthUser.nickname] from `userMetadata["nickname"]`.
     * Fallback to email prefix ONLY for non-Google providers.
     * [AuthUser.avatarUrl] is ignored for Google provider to avoid using Google profile pic.
     *
     * Internal to allow overriding in unit tests (MockK has trouble with UserInfo extension properties).
     */
    internal fun mapUserInfoToAuthUser(userInfo: UserInfo): AuthUser {
        val provider = userInfo.identities?.firstOrNull()?.provider ?: "email"
        val isGoogle = provider == "google"
        val metadata = userInfo.userMetadata

        val nickname = metadata?.get("nickname")?.jsonPrimitive?.contentOrNull
            ?: if (isGoogle) null else userInfo.email?.substringBefore('@')

        // 1. Try "avatar_url" (our custom field from signup/metadata push)
        // 2. Try "picture" (Google standard field)
        val avatarUrl = metadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull
            ?: metadata?.get("picture")?.jsonPrimitive?.contentOrNull

        // Filter out Google profile picture if it's the default one from their CDN.
        // We satisfy the "Do NOT use Google account picture" policy while still
        // allowing our own custom avatar stored in metadata.
        val filteredAvatarUrl = if (isGoogle && avatarUrl?.contains("googleusercontent.com") == true) {
            null
        } else {
            avatarUrl
        }

        return AuthUser(
            id = userInfo.id,
            email = userInfo.email,
            nickname = nickname,
            gameTag = metadata?.get("game_tag")?.jsonPrimitive?.contentOrNull,
            avatarUrl = filteredAvatarUrl,
            provider = provider,
        )
    }

    private fun Throwable.toAuthError(): AuthError = when (this) {
        is RestException -> when (statusCode) {
            400 -> AuthError.InvalidCredentials
            422 -> AuthError.EmailAlreadyInUse
            404 -> AuthError.UserNotFound
            401 -> AuthError.SessionExpired
            else -> AuthError.Unknown(message)
        }

        is retrofit2.HttpException -> when (code()) {
            400 -> AuthError.InvalidCredentials
            422 -> AuthError.EmailAlreadyInUse
            404 -> AuthError.UserNotFound
            401 -> AuthError.SessionExpired
            else -> AuthError.Unknown(message())
        }

        is HttpRequestTimeoutException,
        is IOException -> AuthError.NetworkError

        else -> AuthError.Unknown(message)
    }

    companion object {
        /** Must match the CHECK constraint on the `user_profiles.nickname` column in Supabase. */
        private const val NICKNAME_MAX_LENGTH = 30
        private const val TAG = "AuthRepositoryImpl"
    }

    private fun SessionStatus.toSessionState(): SessionState = when (this) {
        is SessionStatus.Authenticated -> session.user
            ?.let { SessionState.Authenticated(mapUserInfoToAuthUser(it)) }
            ?: SessionState.Unauthenticated

        is SessionStatus.NotAuthenticated -> SessionState.Unauthenticated
        is SessionStatus.Initializing -> SessionState.Loading
        is SessionStatus.RefreshFailure -> SessionState.Unauthenticated
    }
}
