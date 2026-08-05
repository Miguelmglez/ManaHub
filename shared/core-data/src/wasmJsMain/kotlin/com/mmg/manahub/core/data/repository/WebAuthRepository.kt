package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.decodeIsAnonymousClaim
import com.mmg.manahub.core.data.remote.UserProfileClient
import com.mmg.manahub.core.data.remote.dto.UpdateAvatarUrlDto
import com.mmg.manahub.core.data.remote.dto.UpdateNicknameDto
import com.mmg.manahub.core.data.remote.dto.UpsertUserProfileDto
import com.mmg.manahub.core.domain.auth.AuthError
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Web [AuthRepository] implementation (web scope expansion, email/password auth slice,
 * 2026-08-05). Backs [com.mmg.manahub.web.auth.AuthViewModel] via the SAME shared
 * `commonMain` [AuthRepository] interface + [com.mmg.manahub.feature.auth.domain.usecase
 * .SignInWithEmailUseCase]/[com.mmg.manahub.feature.auth.domain.usecase.SignUpWithEmailUseCase]
 * Android already uses -- until this slice, `:webApp` called `SupabaseClient.auth` directly from
 * [com.mmg.manahub.web.auth.AuthViewModel] (a W2a pragmatic shortcut for guest-only sign-in);
 * every prior W3+ repo slice (Trades/Friends/Deck/Collection) found real bugs faster by going
 * through the real shared logic instead of ad-hoc calls, so this repository follows that pattern
 * for the same reason.
 *
 * ## Scope: real methods vs. loud stubs
 * Real: [signInWithEmail], [signUpWithEmail], [signOut], [getCurrentUser], [resetPassword],
 * [updateNickname], [updateAvatarUrl], [signInAnonymously], [sessionState]. Google OAuth
 * ([signInWithGoogle]/[signUpWithGoogle]/[linkGoogleIdentity]) and [deleteAccount] are loud
 * [UnsupportedOperationException] stubs -- NOT a web platform limitation, a deliberate scope cut
 * for this task (Google needs external OAuth client credentials not available yet; deleteAccount
 * is a real but lower-priority Profile-adjacent follow-up). Same "single Nothing-returning helper"
 * pattern [WebCardRepository]/[WebDeckRepository] already established for their own stubbed methods.
 *
 * ## [sessionState] is JWT-metadata-only, NOT `user_profiles`-enriched
 * Android's `AuthRepositoryImpl.sessionState` does a second DB round-trip per auth transition to
 * enrich [AuthUser] with the server-side nickname/gameTag/profileCompleted from `user_profiles`
 * (falling back to an upsert when the row doesn't exist yet). This repository does NOT replicate
 * that enrichment -- [AuthUser.nickname]/[AuthUser.avatarUrl] here come straight from the JWT's
 * `user_metadata` claim, same scope [AuthViewModel]'s pre-refactor `AuthUiState.toStatusLabel()`
 * already had. [com.mmg.manahub.web.profile.ProfileViewModel] remains the sole source of truth for
 * the full profile view (it does its own [UserProfileClient.fetchProfile] call) -- this keeps
 * [sessionState] cheap and avoids a second, subtly-different profile-fetch path. Revisit only if a
 * future screen needs the enriched shape directly off [sessionState].
 *
 * ## Sign-up profile write
 * [signUpWithEmail] passes `nickname`/`avatar_url` as Supabase auth metadata (the `handle_new_user`
 * trigger reads `raw_user_meta_data` and creates the `user_profiles` row from it automatically,
 * same as Android) AND explicitly calls [UserProfileClient.upsertProfile] afterwards as a
 * belt-and-braces guarantee the row carries the right values immediately -- mirrors Android's
 * `AuthRepositoryImpl.signUpWithEmail` calling `UserProfileDataSource.upsertUserProfile` after the
 * same `signUpWith(Email)` call (that Android-only class wraps a Retrofit call this repository has
 * no need for, since [UserProfileClient.upsertProfile] already does the same job over Ktor).
 */
@OptIn(ExperimentalTime::class)
class WebAuthRepository(
    private val supabaseClient: SupabaseClient,
    private val userProfileClient: UserProfileClient,
) : AuthRepository {

    private val auth = supabaseClient.auth
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val sessionState: StateFlow<SessionState> = auth.sessionStatus
        .map { it.toSessionState() }
        .stateIn(repositoryScope, SharingStarted.Eagerly, SessionState.Loading)

    override suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser> =
        runCatching {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val userInfo = auth.currentUserOrNull()
                ?: return@runCatching AuthResult.Error(AuthError.SessionExpired)
            AuthResult.Success(mapUserInfoToAuthUser(userInfo))
        }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        nickname: String,
        avatarUrl: String?,
    ): AuthResult<AuthUser> {
        // Defensive guard, same as Android's AuthRepositoryImpl -- the ViewModel validates first,
        // but a blank nickname reaching this layer is always a caller contract violation.
        val trimmedNickname = nickname.trim()
        if (trimmedNickname.isBlank()) {
            return AuthResult.Error(AuthError.InvalidCredentials)
        }
        return runCatching {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
                this.data = buildJsonObject {
                    put("nickname", trimmedNickname)
                    if (avatarUrl != null) put("avatar_url", avatarUrl)
                }
            }
            // currentUserOrNull() returns null when email confirmation is enabled server-side.
            val userInfo = auth.currentUserOrNull()
                ?: return@runCatching AuthResult.Error(AuthError.EmailConfirmationRequired)

            val baseUser = mapUserInfoToAuthUser(userInfo).copy(
                nickname = trimmedNickname,
                avatarUrl = avatarUrl ?: mapUserInfoToAuthUser(userInfo).avatarUrl,
            )
            userProfileClient.upsertProfile(
                profile = UpsertUserProfileDto(
                    id = baseUser.id,
                    email = baseUser.email,
                    nickname = trimmedNickname,
                    avatarUrl = baseUser.avatarUrl,
                    provider = baseUser.provider,
                    updatedAt = nowMillis(),
                ),
            )
            AuthResult.Success(baseUser)
        }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }
    }

    override suspend fun signInWithGoogle(idToken: String, rawNonce: String): AuthResult<AuthUser> =
        unsupported("signInWithGoogle")

    override suspend fun signUpWithGoogle(
        idToken: String,
        rawNonce: String,
        nickname: String,
        avatarUrl: String?,
    ): AuthResult<AuthUser> = unsupported("signUpWithGoogle")

    override suspend fun signOut(): AuthResult<Unit> = runCatching {
        auth.signOut()
        AuthResult.Success(Unit)
    }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }

    override suspend fun getCurrentUser(): AuthUser? =
        auth.currentUserOrNull()?.let { mapUserInfoToAuthUser(it) }

    override suspend fun resetPassword(email: String): AuthResult<Unit> = runCatching {
        auth.resetPasswordForEmail(email)
        AuthResult.Success(Unit)
    }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }

    override suspend fun deleteAccount(): AuthResult<Unit> = unsupported("deleteAccount")

    override suspend fun updateNickname(nickname: String): AuthResult<AuthUser> {
        if (nickname.length > NICKNAME_MAX_LENGTH) {
            return AuthResult.Error(AuthError.NicknameTooLong)
        }
        return runCatching {
            userProfileClient.updateNickname(UpdateNicknameDto(newNickname = nickname.trim()))
            val userInfo = auth.currentUserOrNull()
                ?: return@runCatching AuthResult.Error(AuthError.SessionExpired)
            AuthResult.Success(mapUserInfoToAuthUser(userInfo).copy(nickname = nickname.trim()))
        }.getOrElse { e ->
            // expectSuccess = true (installSupabaseAuthHeaders) throws ClientRequestException on
            // 4xx -- the RPC returns 400 specifically for inappropriate-content nicknames.
            if (e is ClientRequestException && e.response.status.value == 400) {
                AuthResult.Error(AuthError.NicknameInappropriate)
            } else {
                AuthResult.Error(e.toAuthError())
            }
        }
    }

    override suspend fun linkGoogleIdentity(
        email: String,
        password: String,
        pendingIdToken: String,
        pendingNonce: String,
    ): AuthResult<AuthUser> = unsupported("linkGoogleIdentity")

    override suspend fun updateAvatarUrl(avatarUrl: String?): AuthResult<Unit> = runCatching {
        userProfileClient.updateAvatarUrl(UpdateAvatarUrlDto(newAvatarUrl = avatarUrl))
        AuthResult.Success(Unit)
    }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }

    override suspend fun signInAnonymously(): AuthResult<Unit> = runCatching {
        auth.signInAnonymously()
        AuthResult.Success(Unit)
    }.getOrElse { e -> AuthResult.Error(e.toAuthError()) }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun SessionStatus.toSessionState(): SessionState = when (this) {
        is SessionStatus.Authenticated -> {
            val userInfo = session.user ?: return SessionState.Unauthenticated
            val user = mapUserInfoToAuthUser(userInfo)
                .copy(isAnonymous = decodeIsAnonymousClaim(session.accessToken))
            SessionState.Authenticated(user)
        }
        is SessionStatus.NotAuthenticated -> SessionState.Unauthenticated
        is SessionStatus.Initializing -> SessionState.Loading
        is SessionStatus.RefreshFailure -> SessionState.Unauthenticated
    }

    /**
     * Maps a Supabase [UserInfo] to the domain [AuthUser]. Simplified from Android's
     * `AuthRepositoryImpl.mapUserInfoToAuthUser` -- no Google-provider branching (Google is not
     * implemented on web here), so avatar/nickname always come straight from `user_metadata`.
     * [AuthUser.isAnonymous] is always `false` here for the same reason documented on Android:
     * `is_anonymous` is a top-level JWT claim, not part of [UserInfo] -- only [toSessionState]
     * (the sole path feeding [sessionState]) corrects it via [decodeIsAnonymousClaim].
     */
    private fun mapUserInfoToAuthUser(userInfo: UserInfo): AuthUser {
        val provider = userInfo.identities?.firstOrNull()?.provider ?: "email"
        val metadata = userInfo.userMetadata
        val nickname = metadata?.get("nickname")?.jsonPrimitive?.contentOrNull
            ?: userInfo.email?.substringBefore('@')
        val avatarUrl = metadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull

        return AuthUser(
            id = userInfo.id,
            email = userInfo.email,
            nickname = nickname,
            gameTag = metadata?.get("game_tag")?.jsonPrimitive?.contentOrNull,
            avatarUrl = avatarUrl,
            provider = provider,
            profileCompleted = false,
            isAnonymous = false,
        )
    }

    /**
     * Maps a [Throwable] to a domain [AuthError]. Mirrors Android's `AuthRepositoryImpl
     * .toAuthError` (same [AuthRestException]/[RestException]/[ResponseException] branches, no
     * `isGoogleSignIn` param since Google isn't implemented here) with ONE platform divergence:
     * there is no `java.io.IOException` on wasmJs. A real `fetch()` failure surfaces here as a
     * plain [kotlin.Error] (e.g. `"Fail to fetch"`), never a typed network exception -- see
     * `web/common/ErrorMessages.kt`'s KDoc for the same finding. Falls back to a message-content
     * heuristic for that case since there's no typed exception on this platform to catch instead.
     */
    private fun Throwable.toAuthError(): AuthError = when (this) {
        is AuthRestException -> when (errorCode) {
            AuthErrorCode.EmailNotConfirmed -> AuthError.EmailNotConfirmed
            AuthErrorCode.UserNotFound -> AuthError.UserNotFound
            AuthErrorCode.SessionExpired,
            AuthErrorCode.SessionNotFound -> AuthError.SessionExpired
            AuthErrorCode.EmailExists,
            AuthErrorCode.UserAlreadyExists -> AuthError.EmailAlreadyInUse
            else -> when (statusCode) {
                400 -> AuthError.InvalidCredentials
                422 -> AuthError.EmailAlreadyInUse
                404 -> AuthError.UserNotFound
                401 -> AuthError.SessionExpired
                else -> AuthError.Unknown(message)
            }
        }
        is RestException -> when (statusCode) {
            400 -> AuthError.InvalidCredentials
            422 -> AuthError.EmailAlreadyInUse
            404 -> AuthError.UserNotFound
            401 -> AuthError.SessionExpired
            else -> AuthError.Unknown(message)
        }
        is ResponseException -> when (response.status.value) {
            400 -> AuthError.InvalidCredentials
            422 -> AuthError.EmailAlreadyInUse
            404 -> AuthError.UserNotFound
            401 -> AuthError.SessionExpired
            else -> AuthError.Unknown(message)
        }
        is HttpRequestTimeoutException -> AuthError.NetworkError
        else -> {
            val msg = message?.lowercase().orEmpty()
            if (msg.contains("fetch") || msg.contains("network")) {
                AuthError.NetworkError
            } else {
                AuthError.Unknown(message)
            }
        }
    }

    private fun unsupported(op: String): Nothing = throw UnsupportedOperationException(
        "$op is not implemented on web (deliberately out of scope -- see WebAuthRepository's class KDoc)."
    )

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private companion object {
        /** Must match the CHECK constraint on `user_profiles.nickname` (mirrors Android's constant). */
        const val NICKNAME_MAX_LENGTH = 30
    }
}
