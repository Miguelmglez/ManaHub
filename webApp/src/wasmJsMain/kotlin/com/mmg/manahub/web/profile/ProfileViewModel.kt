package com.mmg.manahub.web.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.decodeIsAnonymousClaim
import com.mmg.manahub.core.data.remote.UserProfileClient
import com.mmg.manahub.core.data.remote.dto.UpdateNicknameDto
import com.mmg.manahub.web.common.toUserFacingMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Resolved state for [ProfileScreen] (web scope expansion, approved 2026-08-04 -- Settings ->
 * Profile -> Add Card). Mirrors [com.mmg.manahub.web.auth.AuthUiState]'s own security discipline:
 * never exposes the raw `SessionStatus`/`UserSession` to the UI layer (a `toString()`/full-object
 * render would leak the raw JWT access + refresh tokens).
 *
 * [Guest] is a DISTINCT state from [Loaded] on purpose, not just a `Loaded` with null fields --
 * anonymous (guest) sessions have NO `user_profiles` row at all (see CLAUDE.md's Online sessions
 * invariant: "Never call `upsertUserProfile` for anonymous users -- they have no `user_profiles`
 * row"). Collapsing the two would either (a) issue a `fetchProfile` call guaranteed to return
 * nothing, or (b) let a guest attempt a nickname save that has no row to update against.
 */
sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data object SignedOut : ProfileUiState
    data class Guest(val userId: String) : ProfileUiState
    data class Loaded(
        val userId: String,
        val nickname: String?,
        val avatarUrl: String?,
        val gameTag: String?,
    ) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

/**
 * One-shot [ProfileViewModel] events, delivered via a buffered [Channel] -- never a nullable
 * [MutableStateFlow] (CLAUDE.md: "One-shot events use a buffered Channel ... never a nullable
 * MutableStateFlow", a StateFlow would equality-collapse a second identical event and can drop one
 * across a lifecycle pause). [SignedOut] tells [ProfileScreen] to navigate back to the Account
 * surface once sign-out actually completes server/SDK-side, rather than navigating optimistically.
 */
sealed interface ProfileEvent {
    data object SignedOut : ProfileEvent
}

/**
 * Backs [ProfileScreen] -- a MINIMAL profile surface (nickname edit, avatar display-only, sign
 * out) per the web scope expansion's explicit exclusion list: no achievements/stats/tier/level/
 * cosmetics UI (that's the gamification system, out of web v1 scope), no `?tab=` deep-link arg,
 * no avatar UPLOAD (would need a Storage bucket + file picker; this slice only ever reads
 * [UserProfileClient.fetchProfile]'s existing `avatar_url` column).
 *
 * Reuses the SAME [SupabaseClient]/`auth.sessionStatus` pattern as
 * [com.mmg.manahub.web.auth.AuthViewModel] (own instance, not shared -- Koin `viewModel {}` scopes
 * per nav back-stack entry) rather than inventing a second session-observation mechanism. Sign-out
 * goes through the identical `auth.signOut()` call Android's `AuthRepositoryImpl.signOut()` uses
 * (no `SignOutScope.LOCAL` override here -- that Android-only branch exists for a token-already-
 * revoked edge case this screen doesn't need to special-case).
 */
class ProfileViewModel(
    supabaseClient: SupabaseClient,
    private val userProfileClient: UserProfileClient,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    private val auth = supabaseClient.auth

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _nicknameInput = MutableStateFlow("")
    val nicknameInput: StateFlow<String> = _nicknameInput.asStateFlow()

    private val _isSavingNickname = MutableStateFlow(false)
    val isSavingNickname: StateFlow<Boolean> = _isSavingNickname.asStateFlow()

    private val _nicknameError = MutableStateFlow<String?>(null)
    val nicknameError: StateFlow<String?> = _nicknameError.asStateFlow()

    private val _nicknameSaved = MutableStateFlow(false)
    val nicknameSaved: StateFlow<Boolean> = _nicknameSaved.asStateFlow()

    private val _isSigningOut = MutableStateFlow(false)
    val isSigningOut: StateFlow<Boolean> = _isSigningOut.asStateFlow()

    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events: Flow<ProfileEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            auth.sessionStatus.collect { status -> handleSessionStatus(status) }
        }
    }

    private suspend fun handleSessionStatus(status: SessionStatus) {
        when (status) {
            is SessionStatus.Authenticated -> {
                val userId = status.session.user?.id
                if (userId.isNullOrBlank()) {
                    _uiState.value = ProfileUiState.Error("No user id in session.")
                    return
                }
                // is_anonymous is a TOP-LEVEL JWT claim -- see decodeIsAnonymousClaim's KDoc
                // (shared/core-common) and AuthViewModel's identical usage.
                val isAnonymous = decodeIsAnonymousClaim(status.session.accessToken)
                if (isAnonymous) {
                    _uiState.value = ProfileUiState.Guest(userId = userId)
                } else {
                    loadProfile(userId)
                }
            }
            is SessionStatus.NotAuthenticated -> _uiState.value = ProfileUiState.SignedOut
            is SessionStatus.Initializing -> _uiState.value = ProfileUiState.Loading
            is SessionStatus.RefreshFailure -> _uiState.value = ProfileUiState.SignedOut
        }
    }

    /**
     * Fetches the profile row from `user_profiles` via [UserProfileClient.fetchProfile] and
     * publishes it as [ProfileUiState.Loaded]. Also called after [saveNickname] succeeds, as the
     * server-truth re-read that proves the write actually persisted -- never an optimistic local
     * field update.
     */
    private suspend fun loadProfile(userId: String) {
        try {
            val profile = userProfileClient.fetchProfile(idFilter = "eq.$userId").firstOrNull()
            _uiState.value = ProfileUiState.Loaded(
                userId = userId,
                nickname = profile?.nickname,
                avatarUrl = profile?.avatarUrl,
                gameTag = profile?.gameTag,
            )
            _nicknameInput.value = profile?.nickname.orEmpty()
        } catch (e: Throwable) {
            // wasmJs: a real fetch() failure surfaces as kotlin.Error, not kotlin.Exception --
            // must catch Throwable, never Exception (see toUserFacingMessage's KDoc).
            _uiState.value = ProfileUiState.Error(e.toUserFacingMessage("load profile", crashReporter))
        }
    }

    fun onNicknameInputChanged(value: String) {
        _nicknameInput.value = value
        _nicknameError.value = null
        _nicknameSaved.value = false
    }

    /**
     * Saves the nickname via the `update_user_nickname` RPC, then re-fetches the profile from the
     * server ([loadProfile]) to confirm the write actually persisted. No-ops when not in
     * [ProfileUiState.Loaded] (there is no row to update for a guest) or while a save is already
     * in flight.
     */
    fun saveNickname() {
        val state = _uiState.value
        if (state !is ProfileUiState.Loaded || _isSavingNickname.value) return
        val trimmed = _nicknameInput.value.trim()
        if (trimmed.isEmpty()) {
            _nicknameError.value = "Nickname cannot be empty."
            return
        }
        if (trimmed.length > NICKNAME_MAX_LENGTH) {
            _nicknameError.value = "Nickname must be $NICKNAME_MAX_LENGTH characters or fewer."
            return
        }
        viewModelScope.launch {
            _isSavingNickname.value = true
            _nicknameError.value = null
            _nicknameSaved.value = false
            try {
                userProfileClient.updateNickname(UpdateNicknameDto(newNickname = trimmed))
                loadProfile(state.userId)
                _nicknameSaved.value = true
            } catch (e: ClientRequestException) {
                // The RPC returns HTTP 400 specifically for inappropriate-content nicknames --
                // same mapping as Android's AuthRepositoryImpl.updateNicknameInternal.
                _nicknameError.value = if (e.response.status.value == 400) {
                    "That nickname isn't allowed. Try another one."
                } else {
                    e.toUserFacingMessage("save nickname", crashReporter)
                }
            } catch (e: Throwable) {
                _nicknameError.value = e.toUserFacingMessage("save nickname", crashReporter)
            } finally {
                _isSavingNickname.value = false
            }
        }
    }

    /** Signs out. No-ops on a double-tap while a sign-out is already in flight. */
    fun signOut() {
        if (_isSigningOut.value) return
        viewModelScope.launch {
            _isSigningOut.value = true
            try {
                auth.signOut()
                _events.send(ProfileEvent.SignedOut)
            } catch (e: Throwable) {
                crashReporter.recordException(e)
            } finally {
                _isSigningOut.value = false
            }
        }
    }

    private companion object {
        // Mirrors Android's AuthRepositoryImpl/AuthViewModel NICKNAME_MAX_LENGTH -- kept as a
        // local web-side constant rather than a shared one since no shared nickname-validation
        // module exists yet (same scope-cut as this screen's other web-local constants).
        const val NICKNAME_MAX_LENGTH = 30
    }
}
