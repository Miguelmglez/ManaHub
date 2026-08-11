package com.mmg.manahub.web.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.auth.AuthError
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.feature.auth.domain.usecase.SignInWithEmailUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignUpWithEmailUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Resolved authentication state for [AuthScreen] (web roadmap W2a). Deliberately does NOT expose
 * the raw [SessionState]/Supabase session object to the UI layer -- per the W0 spike's own security
 * finding (`project_kmp_spike_findings` memory), rendering a session object directly would leak the
 * raw JWT access + refresh tokens. Only the specific, non-sensitive fields the UI needs are
 * extracted here.
 *
 * No longer carries an `Error` case (removed in the email/password auth slice, 2026-08-05): every
 * action-specific failure (sign-in, sign-up, password reset) now surfaces inline via
 * [AuthFormState.formError]/[AuthFormState.infoMessage] instead of overloading the session-status
 * display with a transient action error.
 */
sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object SignedOut : AuthUiState
    data class SignedIn(val userId: String, val isAnonymous: Boolean) : AuthUiState
}

enum class AuthFormMode { SIGN_IN, SIGN_UP }

/**
 * Form-local state for the email/password sign-in and sign-up flows on [AuthScreen]. Kept
 * separate from [AuthUiState] (which only reflects the resolved session) so a mid-typing form
 * doesn't get clobbered by an unrelated session-status re-emission.
 *
 * [infoMessage] is a neutral, non-error confirmation (email confirmation pending, password reset
 * link sent) rendered with different styling than [formError] -- see [AuthScreen].
 */
data class AuthFormState(
    val mode: AuthFormMode = AuthFormMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val nickname: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val nicknameError: String? = null,
    val formError: String? = null,
    val infoMessage: String? = null,
)

/**
 * Backs [AuthScreen]. Web scope expansion (2026-08-05): adds real email/password sign-in, sign-up,
 * and password-reset -- Google OAuth remains a deliberately separate follow-up (needs external
 * OAuth client credentials this task doesn't have). Anonymous/guest sign-in was removed
 * project-wide (Supabase Anonymous Sign-In elimination, 2026-08-10): no-account users are local
 * storage only and never touch Supabase Auth.
 *
 * Routes every action through the shared `commonMain` [AuthRepository]/use-case layer (backed here
 * by `WebAuthRepository`, `shared/core-data` wasmJsMain) instead of calling the Supabase SDK
 * directly -- the pre-refactor version of this ViewModel called `SupabaseClient.auth` directly as a
 * W2a pragmatic shortcut; every prior W3+ repo slice found real bugs faster by going through the
 * real shared logic instead, so this follows the same pattern once there was real business logic
 * (nickname validation, profile upsert, typed [AuthError] mapping) worth sharing with Android.
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val signInWithEmailUseCase: SignInWithEmailUseCase,
    private val signUpWithEmailUseCase: SignUpWithEmailUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _formState = MutableStateFlow(AuthFormState())
    val formState: StateFlow<AuthFormState> = _formState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { state ->
                _uiState.value = state.toUiState()
            }
        }
    }

    /** Switches between sign-in and sign-up, resetting every field/error (a stale error from the
     * previous mode must never survive the toggle). No-ops while a request is in flight. */
    fun setMode(mode: AuthFormMode) {
        if (_isSigningIn.value) return
        _formState.value = AuthFormState(mode = mode)
    }

    fun onEmailChanged(value: String) {
        _formState.update { it.copy(email = value, emailError = null, formError = null, infoMessage = null) }
    }

    fun onPasswordChanged(value: String) {
        _formState.update { it.copy(password = value, passwordError = null, formError = null) }
    }

    fun onConfirmPasswordChanged(value: String) {
        _formState.update { it.copy(confirmPassword = value, confirmPasswordError = null, formError = null) }
    }

    fun onNicknameChanged(value: String) {
        _formState.update { it.copy(nickname = value, nicknameError = null, formError = null) }
    }

    /** Validates the current form, then submits sign-in or sign-up depending on [AuthFormState.mode]. */
    fun submit() {
        if (_isSigningIn.value) return
        val state = _formState.value
        val email = state.email.trim()

        val emailError = validateEmail(email)
        val passwordError = validatePassword(state.password)
        val isSignUp = state.mode == AuthFormMode.SIGN_UP
        val confirmError = if (isSignUp && state.password != state.confirmPassword) {
            "Passwords don't match."
        } else null
        val nicknameError = if (isSignUp && state.nickname.trim().isBlank()) {
            "Nickname is required."
        } else null

        if (emailError != null || passwordError != null || confirmError != null || nicknameError != null) {
            _formState.update {
                it.copy(
                    emailError = emailError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmError,
                    nicknameError = nicknameError,
                )
            }
            return
        }

        viewModelScope.launch {
            _isSigningIn.value = true
            _formState.update { it.copy(formError = null, infoMessage = null) }
            val result = if (isSignUp) {
                signUpWithEmailUseCase(email = email, password = state.password, nickname = state.nickname.trim())
            } else {
                signInWithEmailUseCase(email = email, password = state.password)
            }
            when (result) {
                is AuthResult.Success -> Unit // uiState updates via the sessionState collector above
                is AuthResult.Error -> {
                    if (result.error is AuthError.EmailConfirmationRequired) {
                        _formState.update {
                            it.copy(infoMessage = result.error.toUiMessage(), formError = null)
                        }
                    } else {
                        _formState.update {
                            it.copy(formError = result.error.toUiMessage(), infoMessage = null)
                        }
                    }
                }
            }
            _isSigningIn.value = false
        }
    }

    /**
     * Sends a password-reset email for the currently entered address. Deliberately does NOT
     * distinguish "no account for this email" from success in [AuthFormState.infoMessage] --
     * confirming account existence either way would be an email-enumeration leak.
     */
    fun requestPasswordReset() {
        if (_isSigningIn.value) return
        val email = _formState.value.email.trim()
        val emailError = validateEmail(email)
        if (emailError != null) {
            _formState.update { it.copy(emailError = emailError) }
            return
        }
        viewModelScope.launch {
            _isSigningIn.value = true
            _formState.update { it.copy(formError = null, infoMessage = null) }
            when (val result = authRepository.resetPassword(email)) {
                is AuthResult.Success -> _formState.update {
                    it.copy(infoMessage = "If an account exists for that email, a password reset link is on its way.")
                }
                is AuthResult.Error -> _formState.update { it.copy(formError = result.error.toUiMessage()) }
            }
            _isSigningIn.value = false
        }
    }

    private fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Email is required."
        !EMAIL_REGEX.matches(email) -> "Enter a valid email address."
        else -> null
    }

    private fun validatePassword(password: String): String? = when {
        password.isBlank() -> "Password is required."
        password.length < MIN_PASSWORD_LENGTH -> "Password must be at least $MIN_PASSWORD_LENGTH characters."
        else -> null
    }

    private fun SessionState.toUiState(): AuthUiState = when (this) {
        is SessionState.Loading -> AuthUiState.Loading
        is SessionState.Unauthenticated -> AuthUiState.SignedOut
        is SessionState.Authenticated -> AuthUiState.SignedIn(userId = user.id, isAnonymous = user.isAnonymous)
    }

    /**
     * Renders only a short, generic, user-facing message per [AuthError] case -- copy mirrors
     * Android's `feature.auth.presentation.AuthViewModel.toUiMessage()` (`strings.xml`
     * `auth_error_*` keys) so the two platforms read consistently, without pulling in Android
     * string resources here.
     */
    private fun AuthError.toUiMessage(): String = when (this) {
        is AuthError.InvalidCredentials -> "Incorrect email or password."
        is AuthError.EmailAlreadyInUse -> "This email is already registered."
        is AuthError.NetworkError -> "No connection. Check your network."
        is AuthError.SessionExpired -> "Session expired. Sign in again."
        is AuthError.UserNotFound -> "User not found."
        is AuthError.EmailConfirmationRequired -> "Check your inbox to confirm your email, then sign in."
        is AuthError.EmailNotConfirmed ->
            "Please confirm your email first. Check your inbox for the confirmation link, then sign in."
        is AuthError.NicknameInappropriate -> "This nickname contains inappropriate content. Please choose another."
        is AuthError.NicknameTooLong -> "Nickname must be 30 characters or less."
        is AuthError.SingleIdentityNotDeletable -> "You need another sign-in method before removing this one."
        is AuthError.RateLimited -> "Too many requests. Please wait a moment before trying again."
        is AuthError.GoogleEmailConflict -> "An account already exists with this email."
        is AuthError.NoProfileFound -> "No profile found for this account."
        is AuthError.Unknown -> "An unexpected error occurred. Try again."
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
        val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
