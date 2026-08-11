package com.mmg.manahub.feature.auth.data.repository

import android.util.Log
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.common.decodeAmrIncludesRecoveryClaim
import com.mmg.manahub.core.common.decodeIsAnonymousClaim
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.UserProfileClient
import com.mmg.manahub.core.data.remote.dto.UpdateAvatarUrlDto
import com.mmg.manahub.core.data.remote.dto.UpdateNicknameDto
import com.mmg.manahub.core.data.remote.dto.UserProfileDto
import com.mmg.manahub.feature.auth.data.remote.ProfileFetchResult
import com.mmg.manahub.feature.auth.data.remote.UserProfileDataSource
import com.mmg.manahub.core.domain.auth.AuthError
import com.mmg.manahub.core.domain.auth.AuthIdentity
import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.util.recordNonFatal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom

/**
 * KMP migration — Hilt→Koin cutover batch 5: natively Koin-constructed in `app.di.coreBridgeKoinModule`
 * (the feature-private Hilt `AuthModule`, which used to `@Binds` this class, was deleted). The
 * `@Named("supabase")`/`@ApplicationScope`/`@IoDispatcher` Hilt qualifiers are gone — Koin resolves the
 * equivalent deps positionally via `get(named("supabase"))` / a shared `CoroutineScope` single /
 * `Dispatchers.IO` at the call site instead.
 */
class AuthRepositoryImpl(
    private val supabaseAuth: Auth,
    private val userProfileDataSource: UserProfileDataSource,
    private val userProfileClient: UserProfileClient,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val supabaseOkHttpClient: OkHttpClient,
    private val applicationScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    private val profileRefreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Google-linked-identity tracking for [trackIdentityLinkEvents]. `null` until the first
     * [SessionState.Authenticated] emission is observed in this process; reset to `null` on
     * sign-out. A class-level field (not local to the flow) so it survives `sessionState`'s
     * `WhileSubscribed(5_000)` restarts across collector churn — only a real sign-out or process
     * restart should reset the baseline.
     */
    private var knownIdentityProviders: Set<String>? = null

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
        // [linkGoogleIdentityNative] only returns the OAuth authorization URL — the actual link
        // completes asynchronously via MainActivity's `supabaseClient.handleDeeplinks(intent)`,
        // which updates the SDK session and makes `sessionStatus` re-emit with the newly-linked
        // identity. This is therefore the single reliable SUCCESS observation point for that flow
        // (the SDK has no dedicated link-completed callback to hook instead).
        .onEach { state -> trackIdentityLinkEvents(state) }
        .flatMapLatest { state ->
            if (state !is SessionState.Authenticated) {
                flowOf(state)
            } else if (state.user.isAnonymous) {
                // Anonymous users have no user_profiles row — skip profile fetch and upsert.
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

            val baseUser = mapUserInfoToAuthUser(userInfo)
            val user = baseUser.copy(
                nickname = trimmedNickname,
                avatarUrl = avatarUrl ?: baseUser.avatarUrl,
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
            // new auth.users entry (including Google OAuth), so a "not found" result
            // really means the user is brand new. The correct gate is profile_completed:
            //   - FALSE → user is new, has not chosen a nickname yet → redirect to sign-up
            //   - TRUE  → returning user, proceed to HomeScreen
            //
            // A FETCH FAILURE (HTTP 401/403/network) is NOT the same as "not found": just
            // after signInWith(IDToken) the SDK session token may not have propagated to the
            // OkHttp interceptor yet, so the RPC is rejected with the anon key. Treating that
            // as "not found" would send a returning, fully-completed user back to the sign-up
            // screen. We therefore distinguish the two via ProfileFetchResult, retry once, then
            // fall back to a direct table query before giving up with a NETWORK error (never
            // NoProfileFound).
            var fetchResult = userProfileDataSource.getProfileByUserId(userInfo.id)
            if (fetchResult is ProfileFetchResult.Failure) {
                // Retry once: the session token usually propagates within a few hundred ms.
                delay(PROFILE_FETCH_RETRY_DELAY_MS)
                fetchResult = userProfileDataSource.getProfileByUserId(userInfo.id)
            }

            val profile: UserProfileDto? = when (fetchResult) {
                is ProfileFetchResult.Found -> fetchResult.profile
                is ProfileFetchResult.NotFound -> null
                is ProfileFetchResult.Failure -> {
                    // Both RPC attempts failed. Fall back to the direct table query as a last
                    // resort — it never returns profile_completed reliably for fresh rows, but for
                    // a returning user the row exists and carries the real flag.
                    val fallback = userProfileDataSource.fetchUserProfile(userInfo.id)
                    if (fallback == null) {
                        // We genuinely could not read the profile. Do NOT misreport this as a
                        // missing profile (which would force the user into sign-up); surface a
                        // network error so the UI can offer a retry. Keep the session intact.
                        return@runCatching AuthResult.Error(AuthError.NetworkError)
                    }
                    fallback
                }
            }

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

            val finalUser = mapUserInfoToAuthUser(userInfo).copy(
                nickname = profile.nickname,
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

            // Best-effort enrichment: a fetch failure here is non-fatal (the identity was
            // already linked above), so anything other than Found simply enriches with nothing.
            val profile = (userProfileDataSource.getProfileByUserId(userInfo.id)
                as? ProfileFetchResult.Found)?.profile

            val finalUser = mapUserInfoToAuthUser(userInfo).copy(
                nickname = profile?.nickname,
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
     * Calls the `update_user_nickname` Supabase RPC via [UserProfileClient], then re-fetches
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
            userProfileClient.updateNickname(
                UpdateNicknameDto(newNickname = nickname.trim())
            )
            val user = supabaseAuth.currentUserOrNull()
                ?: return AuthResult.Error(AuthError.SessionExpired)

            val updatedUser = mapUserInfoToAuthUser(user).copy(nickname = nickname.trim())
            syncToDataStore(updatedUser)
            profileRefreshSignal.tryEmit(Unit)

            AuthResult.Success(updatedUser)
        }.getOrElse { e ->
            // Ktor expectSuccess = true throws ClientRequestException on 4xx.
            if (e is ClientRequestException && e.response.status.value == 400) {
                AuthResult.Error(AuthError.NicknameInappropriate)
            } else {
                AuthResult.Error(e.toAuthError())
            }
        }
    }

    override suspend fun updateAvatarUrl(avatarUrl: String?): AuthResult<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                userProfileClient.updateAvatarUrl(
                    UpdateAvatarUrlDto(newAvatarUrl = avatarUrl)
                )

                supabaseAuth.currentUserOrNull()?.let { mapUserInfoToAuthUser(it) }?.copy(avatarUrl = avatarUrl)?.let {
                    syncToDataStore(it)
                }
                profileRefreshSignal.tryEmit(Unit)

                AuthResult.Success(Unit)
            }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
        }

    override suspend fun resendConfirmationEmail(email: String): AuthResult<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                supabaseAuth.resendEmail(OtpType.Email.SIGNUP, email)
                AuthResult.Success(Unit)
            }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
        }

    override suspend fun requestReauthentication(): AuthResult<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                supabaseAuth.reauthenticate()
                AuthResult.Success(Unit)
            }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
        }

    override suspend fun updateEmail(newEmail: String, code: String): AuthResult<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                // updateCurrentUser defaults to true, so the SDK's own sessionStatus/currentUser
                // reflect the new email in-place — sessionState re-emits without a manual signal.
                supabaseAuth.updateUser {
                    this.email = newEmail
                    this.nonce = code
                }
                notifyAccountEvent(AccountNotificationEvent.EMAIL_CHANGED, providerMetadata("email"))
                AuthResult.Success(Unit)
            }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
        }

    override suspend fun confirmEmailUpdate(newEmail: String): AuthResult<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                // No nonce here: Supabase's "Secure email change" project setting double-confirms
                // via links sent to BOTH the old and new inbox, which already protects this path
                // (see the KDoc on AuthRepository.confirmEmailUpdate).
                supabaseAuth.updateUser {
                    this.email = newEmail
                }
                notifyAccountEvent(AccountNotificationEvent.EMAIL_CHANGED, providerMetadata("email"))
                AuthResult.Success(Unit)
            }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
        }

    override suspend fun updatePassword(newPassword: String, code: String): AuthResult<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                supabaseAuth.updateUser {
                    this.password = newPassword
                    this.nonce = code
                }
                notifyAccountEvent(AccountNotificationEvent.PASSWORD_CHANGED, providerMetadata("email"))
                AuthResult.Success(Unit)
            }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
        }

    override suspend fun unlinkIdentity(identityId: String): AuthResult<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                // Resolve the identity's provider BEFORE unlinking — it disappears from the current
                // user's identity list immediately after, and the notification metadata needs it.
                val provider = supabaseAuth.currentUserOrNull()
                    ?.identities
                    ?.firstOrNull { (it.identityId ?: it.id) == identityId }
                    ?.provider

                supabaseAuth.unlinkIdentity(identityId = identityId, updateLocalUser = true)

                notifyAccountEvent(AccountNotificationEvent.IDENTITY_REMOVED, providerMetadata(provider))

                AuthResult.Success(Unit)
            }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
        }

    override suspend fun linkGoogleIdentityNative(redirectUrl: String): AuthResult<String?> =
        withContext(ioDispatcher) {
            runCatching {
                // The plain auth-kt module never launches a browser itself; it returns the
                // authorization URL for the caller to open (e.g. via Custom Tabs). The
                // OAuth-redirect callback is caught by MainActivity's already-wired
                // supabaseClient.handleDeeplinks(intent), which completes the link.
                val authorizationUrl = supabaseAuth.linkIdentity(Google, redirectUrl)
                AuthResult.Success(authorizationUrl)
            }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
        }

    override suspend fun confirmPasswordReset(newPassword: String): AuthResult<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                // No nonce here: the recovery deep link already imported a fully-authenticated
                // temporary session (see the KDoc on AuthRepository.confirmPasswordReset).
                supabaseAuth.updateUser {
                    this.password = newPassword
                }
                notifyAccountEvent(AccountNotificationEvent.PASSWORD_CHANGED, providerMetadata("email"))
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
                    if (BuildConfig.DEBUG) {
                        val errorBody = resp.body?.string() ?: ""
                        Log.w(TAG, "delete-current-user failed HTTP ${resp.code}: $errorBody")
                    }
                    return@runCatching AuthResult.Error(AuthError.Unknown("HTTP ${resp.code}"))
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
     * Events accepted by the `send-account-notification` Edge Function's `event` field. Values
     * match the deployed contract exactly — do not rename without updating the Edge Function.
     */
    private enum class AccountNotificationEvent(val value: String) {
        PASSWORD_CHANGED("password_changed"),
        EMAIL_CHANGED("email_changed"),
        IDENTITY_LINKED("identity_linked"),
        IDENTITY_REMOVED("identity_removed"),
    }

    /**
     * Builds the `metadata` map for [notifyAccountEvent]. Returns an empty map when [provider] is
     * null (e.g. the identity's provider could not be resolved) so the call still goes out rather
     * than being dropped — the Edge Function's contract treats `metadata` as informational only.
     */
    private fun providerMetadata(provider: String?): Map<String, String> =
        provider?.let { mapOf("provider" to it) } ?: emptyMap()

    /**
     * Tracks the authenticated user's identity providers across [sessionState] re-emissions to
     * fire a best-effort `identity_linked` notification exactly once when a NEW Google identity
     * appears on an ALREADY-known session — see the KDoc on the `.onEach` call site in
     * [sessionState] for why this is the correct observation point for that flow's success.
     *
     * [knownIdentityProviders] being `null` guards against firing on a fresh sign-in/sign-up
     * (where "google" appearing for the first time is not a LINK event, just a normal
     * authentication), and is reset to `null` on sign-out so a different user signing in
     * afterward starts from a clean baseline.
     */
    private fun trackIdentityLinkEvents(state: SessionState) {
        if (state !is SessionState.Authenticated) {
            knownIdentityProviders = null
            return
        }
        val currentProviders = state.user.identities.map { it.provider }.toSet()
        val previousProviders = knownIdentityProviders
        if (previousProviders != null &&
            "google" !in previousProviders &&
            "google" in currentProviders
        ) {
            notifyAccountEvent(AccountNotificationEvent.IDENTITY_LINKED, providerMetadata("google"))
        }
        knownIdentityProviders = currentProviders
    }

    /**
     * Fires a best-effort call to the `send-account-notification` Edge Function after a sensitive
     * account operation has ALREADY succeeded (password/email change, identity link/unlink).
     *
     * Fire-and-forget by design: launches on [applicationScope] (never blocks the caller's
     * suspend function) and swallows every failure — missing session, network error, non-2xx
     * response — via [recordNonFatal]. A failed/undelivered notification email must NEVER be
     * surfaced as if the underlying sensitive operation itself failed, since that operation
     * already succeeded before this call is even made. Only the event type and a generic failure
     * indicator are logged — never the access token, password, email, or reauthentication code.
     *
     * Reuses [supabaseOkHttpClient] (the same client [callSetGoogleAccountPasswordEdgeFunction]
     * uses) with an explicit Authorization header carrying the CALLING USER's own access token —
     * required by the Edge Function's contract to authorize the `user_id` it is told to notify.
     */
    private fun notifyAccountEvent(
        event: AccountNotificationEvent,
        metadata: Map<String, String> = emptyMap(),
    ) {
        applicationScope.launch(ioDispatcher) {
            runCatching {
                val accessToken = supabaseAuth.currentSessionOrNull()?.accessToken
                    ?: return@runCatching
                val userId = supabaseAuth.currentUserOrNull()?.id
                    ?: return@runCatching

                val metadataJson = JSONObject()
                metadata.forEach { (key, value) -> metadataJson.put(key, value) }

                val bodyJson = JSONObject()
                    .put("user_id", userId)
                    .put("event", event.value)
                    .put("metadata", metadataJson)
                    .toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("${BuildConfig.SUPABASE_URL}/functions/v1/send-account-notification")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(bodyJson)
                    .build()

                supabaseOkHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        recordNonFatal(
                            "send_account_notification_failed event_${event.value} http_${response.code}"
                        )
                    }
                }
            }.onFailure { e ->
                recordNonFatal("send_account_notification_error event_${event.value}", e)
            }
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
     * [AuthUser.isAnonymous] is always `false` here: GoTrue puts `is_anonymous` as a TOP-LEVEL
     * claim on the session's JWT access token, not on [UserInfo] itself (verified via `auth-kt`
     * sources — `UserInfo` has no `isAnonymous`/`is_anonymous` field anywhere, nested or not), so
     * this mapper genuinely cannot answer it from a bare `UserInfo`. That default is correct for
     * every call site of this function EXCEPT [toSessionState] (the sole path whose result feeds
     * [AuthRepository.sessionState], which is where every consumer reads `.isAnonymous` from) —
     * every other call site is an email/Google sign-in or profile-update flow that a guest session
     * can never reach. [toSessionState] corrects the field via `.copy(isAnonymous = ...)` using
     * [decodeIsAnonymousClaim] against the session's access token, which DOES carry the claim.
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
            // isAnonymous is intentionally left as the default (false) here — see the KDoc above.
            emailConfirmedAt = userInfo.emailConfirmedAt,
            createdAt = userInfo.createdAt,
            newEmail = userInfo.newEmail,
            identities = userInfo.identities?.map { it.toAuthIdentity() } ?: emptyList(),
        )
    }

    /**
     * Maps a Supabase [io.github.jan.supabase.auth.user.Identity] to the domain [AuthIdentity].
     *
     * [io.github.jan.supabase.auth.user.Identity.identityId] is nullable in the SDK model (unlike
     * [io.github.jan.supabase.auth.user.Identity.id], which is guaranteed non-null) even though
     * `Auth.unlinkIdentity(identityId: String, ...)` requires a non-null value — falls back to
     * [io.github.jan.supabase.auth.user.Identity.id] to always produce a usable, non-null
     * [AuthIdentity.identityId].
     */
    private fun io.github.jan.supabase.auth.user.Identity.toAuthIdentity(): AuthIdentity =
        AuthIdentity(
            identityId = identityId ?: id,
            provider = provider,
            createdAt = createdAt?.let { raw -> runCatching { Instant.parse(raw) }.getOrNull() },
        )

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
        // AuthRestException is a RestException subclass that carries a typed AuthErrorCode.
        // Inspect it first so we can distinguish "email not confirmed" (correct credentials,
        // confirmation still pending — ADR-003) from a genuine bad-password 400, which would
        // otherwise be flattened to the misleading InvalidCredentials message below.
        is AuthRestException -> when (errorCode) {
            AuthErrorCode.EmailNotConfirmed -> AuthError.EmailNotConfirmed
            AuthErrorCode.UserNotFound -> AuthError.UserNotFound
            AuthErrorCode.SessionExpired,
            AuthErrorCode.SessionNotFound -> AuthError.SessionExpired
            // GoTrue refuses to unlink an account's last remaining identity (422). Map it
            // specifically so the UI can show a clear message instead of a generic error, even
            // though the client-side disabled-button guard should prevent this in practice.
            AuthErrorCode.SingleIdentityNotDeletable -> AuthError.SingleIdentityNotDeletable
            AuthErrorCode.EmailExists,
            AuthErrorCode.UserAlreadyExists ->
                if (isGoogleSignIn) AuthError.GoogleEmailConflict("", "", "")
                else AuthError.EmailAlreadyInUse
            else -> when (statusCode) {
                400 -> AuthError.InvalidCredentials
                422 -> if (isGoogleSignIn) AuthError.GoogleEmailConflict("", "", "") else AuthError.EmailAlreadyInUse
                404 -> AuthError.UserNotFound
                401 -> AuthError.SessionExpired
                429 -> AuthError.RateLimited
                else -> AuthError.Unknown(message)
            }
        }

        is RestException -> when (statusCode) {
            400 -> AuthError.InvalidCredentials
            // A placeholder GoogleEmailConflict is returned when isGoogleSignIn=true.
            // The actual email, pendingIdToken and pendingNonce are injected by the caller.
            422 -> if (isGoogleSignIn) AuthError.GoogleEmailConflict("", "", "") else AuthError.EmailAlreadyInUse
            404 -> AuthError.UserNotFound
            401 -> AuthError.SessionExpired
            429 -> AuthError.RateLimited
            else -> AuthError.Unknown(message)
        }

        is ResponseException -> when (response.status.value) {
            400 -> AuthError.InvalidCredentials
            422 -> if (isGoogleSignIn) AuthError.GoogleEmailConflict("", "", "") else AuthError.EmailAlreadyInUse
            404 -> AuthError.UserNotFound
            401 -> AuthError.SessionExpired
            429 -> AuthError.RateLimited
            else -> AuthError.Unknown(message)
        }

        is HttpRequestTimeoutException,
        is IOException -> AuthError.NetworkError

        else -> AuthError.Unknown(message)
    }

    companion object {
        /** Must match the CHECK constraint on the `user_profiles.nickname` column in Supabase. */
        private const val NICKNAME_MAX_LENGTH = 30
        private const val TAG = "AuthRepositoryImpl"

        /**
         * Delay before the single retry of [getProfileByUserId] in [signInWithGoogle].
         * Gives the supabase-kt SDK session a moment to propagate its access token to the
         * OkHttp interceptor, so the retried RPC is authenticated rather than rejected
         * with the anon key.
         */
        private const val PROFILE_FETCH_RETRY_DELAY_MS = 400L
    }

    private fun SessionStatus.toSessionState(): SessionState = when (this) {
        is SessionStatus.Authenticated -> session.user
            ?.let { mapUserInfoToAuthUser(it) }
            // The JWT access token — not UserInfo — is where GoTrue's top-level `is_anonymous`
            // claim and the `amr` (Authentication Methods Reference) claim actually live. This is
            // the ONE call site that corrects both, because this is the sole path that feeds
            // `sessionState`, which every `.isAnonymous`/`.isRecoverySession` consumer reads.
            // `isRecoverySession` is the security-critical signal that gates the no-nonce
            // `confirmPasswordReset` path (see AuthUser.isRecoverySession's KDoc) — it must be set
            // here, not derived from the attacker-controllable `type=recovery` deep-link marker.
            ?.copy(
                isAnonymous = decodeIsAnonymousClaim(session.accessToken),
                isRecoverySession = decodeAmrIncludesRecoveryClaim(session.accessToken),
            )
            ?.let { SessionState.Authenticated(it) }
            ?: SessionState.Unauthenticated

        is SessionStatus.NotAuthenticated -> SessionState.Unauthenticated
        is SessionStatus.Initializing -> SessionState.Loading
        is SessionStatus.RefreshFailure -> SessionState.Unauthenticated
    }
}
