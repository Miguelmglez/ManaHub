package com.mmg.manahub.feature.auth.data.repository

import android.util.Log
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.di.ApplicationScope
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.auth.data.remote.SupabaseUserProfileService
import com.mmg.manahub.feature.auth.data.remote.UpdateAvatarUrlDto
import com.mmg.manahub.feature.auth.data.remote.UpdateNicknameDto
import com.mmg.manahub.feature.auth.data.remote.UserProfileDataSource
import com.mmg.manahub.feature.auth.domain.model.AuthError
import com.mmg.manahub.feature.auth.domain.model.AuthResult
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
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
                    // Fast emit: only emit immediately when the nickname is already known from
                    // in-memory session metadata (i.e. email/password users whose nickname is
                    // embedded in the token). For Google users the in-memory metadata may reflect
                    // stale or provider-supplied values — skip the fast emit and wait for the
                    // server-side profile fetch so the UI never flashes a wrong nickname.
                    val isGoogleUser = state.user.provider == "google"
                    if (!isGoogleUser && state.user.nickname != null) {
                        emit(state)
                    }
                    // Enriched emit: fetch server-side profile (nickname, gameTag, avatarUrl).
                    try {
                        val profile = userProfileDataSource.fetchUserProfile(state.user.id)
                        if (profile != null) {
                            val enrichedUser = state.user.copy(
                                nickname = profile.nickname ?: state.user.nickname,
                                gameTag = profile.gameTag ?: state.user.gameTag,
                                avatarUrl = profile.avatarUrl,
                                profileCompleted = profile.profileCompleted,
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
                        // For Google users we suppressed the fast emit, so we must still
                        // deliver a state so the UI does not hang on Loading forever.
                        if (isGoogleUser) {
                            emit(state)
                        }
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
        // Defensive guard: the ViewModel validates the nickname before calling this, but
        // we enforce it here as well to ensure no code path creates a profile with a null
        // nickname. A blank nickname at this layer is always a caller contract violation.
        val trimmedNickname = nickname.trim()
        if (trimmedNickname.isBlank()) {
            return@withContext AuthResult.Error(AuthError.InvalidCredentials)
        }

        runCatching {
            supabaseAuth.signUpWith(Email) {
                this.email = email
                this.password = password
                // The handle_new_user trigger reads raw_user_meta_data->>'nickname' and
                // inserts it into user_profiles automatically — no extra RPC needed.
                this.data = buildJsonObject {
                    put("nickname", trimmedNickname)
                    if (avatarUrl != null) put("avatar_url", avatarUrl)
                }
            }
            // currentUserOrNull() returns null when email confirmation is enabled.
            val userInfo = supabaseAuth.currentUserOrNull()
                ?: return@runCatching AuthResult.Error(AuthError.EmailConfirmationRequired)

            val user = mapUserInfoToAuthUser(userInfo).copy(
                nickname = trimmedNickname,
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
            // Sign-in with Google: do NOT pass any local data in the metadata block.
            // Injecting the local DataStore nickname (e.g. the default "Wizard") would
            // overwrite the server-side profile nickname for returning users, and would
            // corrupt the metadata for new users who have not yet typed a nickname.
            supabaseAuth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
                nonce = rawNonce
            }
            val userInfo = supabaseAuth.currentUserOrNull()
                ?: return@runCatching AuthResult.Error(AuthError.SessionExpired)

            // Use the get_profile_by_user_id RPC instead of a direct table query.
            // The handle_new_user trigger always creates a user_profiles row for every
            // new auth.users entry (including Google OAuth), so `existingProfile == null`
            // can NEVER be true here. The correct gate is profile_completed:
            //   - FALSE → user is new, has not chosen a nickname yet → redirect to sign-up
            //   - TRUE  → returning user, proceed to HomeScreen
            val profile = userProfileDataSource.getProfileByUserId(userInfo.id)

            if (profile == null || !profile.profileCompleted) {
                // Profile row was created by the trigger but the user has not yet completed
                // the sign-up flow (nickname not chosen). Sign out locally and signal the UI
                // to switch to the Create Account tab so the user can pick a nickname.
                //
                // LOCAL scope avoids a network call with a short-lived token. The auth.users
                // row is harmless until profile_completed = true (RLS policies block access).
                runCatching { supabaseAuth.signOut(SignOutScope.LOCAL) }
                return@runCatching AuthResult.Error(
                    AuthError.NoProfileFound(email = userInfo.email)
                )
            }

            // Returning Google user with a completed profile: use the server data.
            // Guarantee a non-null nickname: fall back to the email prefix as a last resort
            // (profile_completed = true guarantees nickname is set, this is just a safety net).
            val serverNickname = profile.nickname
                ?: userInfo.email?.substringBefore('@')

            val finalUser = mapUserInfoToAuthUser(userInfo).copy(
                nickname = serverNickname,
                gameTag = profile.gameTag,
                avatarUrl = profile.avatarUrl,
                profileCompleted = profile.profileCompleted,
            )

            syncToDataStore(finalUser)
            profileRefreshSignal.tryEmit(Unit)

            AuthResult.Success(finalUser)
        }.getOrElse { e ->
            // On 422, extract the email from the Google ID token JWT so the UI can display
            // it in the linking dialog without asking the user to type it again.
            val authError = e.toAuthError(isGoogleSignIn = true)
            if (authError is AuthError.GoogleEmailConflict) {
                // Build the full GoogleEmailConflict with the pending token data.
                val email = extractEmailFromIdToken(idToken) ?: ""
                AuthResult.Error(AuthError.GoogleEmailConflict(email, idToken, rawNonce))
            } else {
                AuthResult.Error(authError)
            }
        }
    }

    override suspend fun linkGoogleIdentity(
        email: String,
        password: String,
        pendingIdToken: String,
        pendingNonce: String,
    ): AuthResult<AuthUser> = withContext(ioDispatcher) {
        runCatching {
            // Step 1: Authenticate with email/password to obtain a valid session.
            // This verifies the user owns the account before granting identity linking.
            supabaseAuth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            // Step 2: With an active session whose email matches the Google ID token,
            // GoTrue links the Google identity to the existing user instead of creating
            // a new account. From this point forward, both email/password and Google
            // Sign-In will work for this account.
            supabaseAuth.signInWith(IDToken) {
                this.idToken = pendingIdToken
                provider = Google
                nonce = pendingNonce
            }

            val userInfo = supabaseAuth.currentUserOrNull()
                ?: return@runCatching AuthResult.Error(AuthError.SessionExpired)

            val profile = runCatching {
                userProfileDataSource.getProfileByUserId(userInfo.id)
            }.getOrNull()

            val serverNickname = profile?.nickname ?: userInfo.email?.substringBefore('@')

            val finalUser = mapUserInfoToAuthUser(userInfo).copy(
                nickname = serverNickname,
                gameTag = profile?.gameTag,
                avatarUrl = profile?.avatarUrl,
                profileCompleted = profile?.profileCompleted ?: false,
            )

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
        // Defensive guard: the ViewModel validates the nickname before calling this, but
        // we enforce it here as well to ensure no code path creates a profile with a null
        // nickname. A blank nickname at this layer is always a caller contract violation.
        val trimmedNickname = nickname.trim()
        if (trimmedNickname.isBlank()) {
            return@withContext AuthResult.Error(AuthError.InvalidCredentials)
        }

        runCatching {
            supabaseAuth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
                nonce = rawNonce
                // Do NOT inject nickname via metadata here. The handle_new_user trigger
                // creates the profile row with profile_completed = FALSE regardless.
                // The nickname is set atomically by complete_user_profile RPC below.
            }
            val userInfo = supabaseAuth.currentUserOrNull()
                ?: return@runCatching AuthResult.Error(AuthError.SessionExpired)

            // Build a base AuthUser from the Supabase auth info and inject the
            // user-chosen nickname so completeUserProfile can pass it to the RPC.
            val baseUser = mapUserInfoToAuthUser(userInfo).copy(
                nickname = trimmedNickname,
                avatarUrl = avatarUrl,
            )

            // Call the complete_user_profile RPC which atomically:
            //   1. Sets the nickname on the user_profiles row.
            //   2. Marks profile_completed = TRUE.
            // This replaces the old upsertUserProfile call which could not set
            // profile_completed and would bypass the onboarding gate.
            val profileUser = userProfileDataSource.completeUserProfile(baseUser)

            // Fire-and-forget Edge Function to assign a random password, enabling
            // email/password sign-in as a fallback and triggering the welcome email.
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
            // Retrieve the current JWT to authenticate the Edge Function call.
            val accessToken = supabaseAuth.currentSessionOrNull()?.accessToken
                ?: return@runCatching AuthResult.Error(AuthError.SessionExpired)

            // Call the Edge Function which:
            //   1. Deletes all app data (user_card_collection, decks, deck_cards,
            //      game_sessions, tournaments, friendships, user_profiles, etc.)
            //   2. Calls supabase.auth.admin.deleteUser() to fully remove the Auth record
            //      so the same email can re-register immediately.
            // The old `rpc/delete_current_user` only deleted user_profiles and did NOT
            // remove the Auth user, leaving orphaned data behind.
            val request = Request.Builder()
                .url("${BuildConfig.SUPABASE_URL}/functions/v1/delete-current-user")
                .addHeader("Authorization", "Bearer $accessToken")
                .post("".toRequestBody(null))
                .build()

            val httpResponse = supabaseOkHttpClient.newCall(request).execute()
            httpResponse.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string() ?: "HTTP ${resp.code}"
                    return@runCatching AuthResult.Error(AuthError.Unknown(errorBody))
                }
            }

            // Use LOCAL scope: clears the in-memory session without a network call.
            // A GLOBAL signOut would hit the server with an already-deleted token,
            // potentially throw before clearing local state, and leave the session
            // alive — causing the background sync worker to keep hitting Supabase
            // with an invalid token until the JWT TTL expires.
            runCatching { supabaseAuth.signOut(SignOutScope.LOCAL) }
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
            // profileCompleted is intentionally left as the default (false) here.
            // It is only set to true after enrichment from the user_profiles row via
            // the get_profile_by_user_id RPC or the complete_user_profile RPC.
            profileCompleted = false,
        )
    }

    /**
     * Decodes the JWT payload of a Google ID token and extracts the `email` claim.
     *
     * The ID token is a standard JWT with three dot-separated Base64URL-encoded parts.
     * The middle part (index 1) is the JSON payload. No signature verification is needed
     * here because the email is only used for display purposes — GoTrue validates the
     * token on the server when we submit it for linking.
     *
     * @return The email string from the JWT claims, or null if decoding fails for any reason.
     */
    private fun extractEmailFromIdToken(idToken: String): String? = try {
        val payload = idToken.split(".").getOrNull(1) ?: return null
        val decoded = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING))
        org.json.JSONObject(decoded).optString("email").takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.w(TAG, "Failed to extract email from ID token", e)
        null
    }

    /**
     * Maps a [Throwable] thrown by the Supabase SDK or Retrofit to a domain [AuthError].
     *
     * @param isGoogleSignIn When true, a 422 from Supabase is interpreted as a placeholder
     *   [AuthError.GoogleEmailConflict] (the email already exists under a different provider)
     *   rather than the generic [AuthError.EmailAlreadyInUse] that applies during email/password
     *   sign-up. The caller in [signInWithGoogle] enriches this with the actual token data.
     */
    private fun Throwable.toAuthError(isGoogleSignIn: Boolean = false): AuthError = when (this) {
        is RestException -> when (statusCode) {
            400 -> AuthError.InvalidCredentials
            // A placeholder GoogleEmailConflict is returned when isGoogleSignIn=true.
            // The actual email, pendingIdToken and pendingNonce are injected by the caller.
            422 -> if (isGoogleSignIn) AuthError.GoogleEmailConflict("", "", "") else AuthError.EmailAlreadyInUse
            404 -> AuthError.UserNotFound
            401 -> AuthError.SessionExpired
            else -> AuthError.Unknown(message)
        }

        is retrofit2.HttpException -> when (code()) {
            400 -> AuthError.InvalidCredentials
            422 -> if (isGoogleSignIn) AuthError.GoogleEmailConflict("", "", "") else AuthError.EmailAlreadyInUse
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
