package com.mmg.manahub.web.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.remote.UserProfileClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.ktor.client.plugins.ResponseException
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
    private val userProfileClient: UserProfileClient,
) : ViewModel() {

    private val auth = supabaseClient.auth

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    /**
     * W2b plumbing smoke check ONLY -- not a real feature. Proves the web
     * `@Named("supabaseKtor")` [HttpClient][io.ktor.client.HttpClient] (built via
     * [com.mmg.manahub.core.data.remote.installSupabaseAuthHeaders]) reads the LIVE session's
     * access token and successfully round-trips a real Supabase PostgREST call, not just that
     * everything compiles/constructs. An anonymous user has no `user_profiles` row (per CLAUDE.md
     * -- guests use the `anon` role and are never upserted into that table), so an empty result
     * is the EXPECTED clean outcome here, not a bug.
     */
    private val _profileCheck = MutableStateFlow<String?>(null)
    val profileCheck: StateFlow<String?> = _profileCheck.asStateFlow()

    init {
        viewModelScope.launch {
            auth.sessionStatus.collect { status ->
                val resolved = status.toUiState()
                _uiState.value = resolved
                if (resolved is AuthUiState.SignedIn) {
                    runProfileCheck(resolved.userId)
                }
            }
        }
    }

    private fun runProfileCheck(userId: String) {
        viewModelScope.launch {
            _profileCheck.value = "Checking user_profiles via the web Ktor client..."
            _profileCheck.value = try {
                val rows = userProfileClient.fetchProfile(idFilter = "eq.$userId")
                if (rows.isEmpty()) {
                    "OK -- 0 rows (expected for an anonymous session: guests have no " +
                        "user_profiles row)."
                } else {
                    "OK -- ${rows.size} row(s) returned."
                }
            } catch (e: ResponseException) {
                // A non-2xx (e.g. RLS-denied) is a clean, understood outcome for this smoke
                // check -- it still proves the request reached the backend with a Bearer token.
                "HTTP ${e.response.status.value} -- ${e.message ?: "no message"}"
            } catch (e: Exception) {
                "Client-side failure: ${e.message ?: e::class.simpleName}"
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
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "Guest sign-in failed")
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
