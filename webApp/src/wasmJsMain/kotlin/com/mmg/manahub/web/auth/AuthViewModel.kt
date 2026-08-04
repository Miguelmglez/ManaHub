package com.mmg.manahub.web.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.web.common.toUserFacingMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Resolved authentication state for [AuthScreen] (web roadmap W2a). Deliberately does NOT expose
 * the raw [SessionStatus]/`UserSession` to the UI layer -- per the W0 spike's own security
 * finding (`project_kmp_spike_findings` memory), a `SessionStatus.toString()`/full-object render
 * would leak the raw JWT access + refresh tokens on screen and in logs. Only the specific,
 * non-sensitive fields the UI needs are extracted here.
 */
sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object SignedOut : AuthUiState
    data class SignedIn(val userId: String, val isAnonymous: Boolean) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

/**
 * Backs [AuthScreen]. Guest-only for W2a -- Google OAuth is a deliberately separate follow-up task
 * (master plan §2.2 Action 1 continuation), do not add it here.
 */
class AuthViewModel(
    supabaseClient: SupabaseClient,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    private val auth = supabaseClient.auth

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    init {
        viewModelScope.launch {
            auth.sessionStatus.collect { status ->
                _uiState.value = status.toUiState()
            }
        }
    }

    /** Signs in anonymously (guest). No-ops on a double-tap while a sign-in is already in flight. */
    fun signInAsGuest() {
        if (_isSigningIn.value) return
        viewModelScope.launch {
            _isSigningIn.value = true
            try {
                auth.signInAnonymously()
                // uiState updates via the sessionStatus collector above once the auth plugin
                // publishes the new Authenticated status -- no need to set it here directly.
            } catch (e: Throwable) {
                _uiState.value = AuthUiState.Error(e.toUserFacingMessage("sign in as guest", crashReporter))
            } finally {
                _isSigningIn.value = false
            }
        }
    }

    private fun SessionStatus.toUiState(): AuthUiState = when (this) {
        is SessionStatus.Authenticated -> {
            val isAnonymous = session.user
                ?.appMetadata
                ?.get("is_anonymous")
                ?.jsonPrimitive
                ?.booleanOrNull == true
            AuthUiState.SignedIn(userId = session.user?.id.orEmpty(), isAnonymous = isAnonymous)
        }
        is SessionStatus.NotAuthenticated -> AuthUiState.SignedOut
        is SessionStatus.Initializing -> AuthUiState.Loading
        is SessionStatus.RefreshFailure -> AuthUiState.SignedOut
    }
}
