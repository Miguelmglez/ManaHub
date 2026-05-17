package com.mmg.manahub.feature.auth.presentation

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.R
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.feature.auth.domain.model.AuthError
import com.mmg.manahub.feature.auth.domain.model.AuthResult
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.usecase.DeleteAccountUseCase
import com.mmg.manahub.feature.auth.domain.usecase.GetSessionStateUseCase
import com.mmg.manahub.feature.auth.domain.usecase.ResetPasswordUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignInWithEmailUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignInWithGoogleUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignOutUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignUpWithEmailUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignUpWithGoogleUseCase
import com.mmg.manahub.feature.auth.domain.usecase.LinkGoogleIdentityUseCase
import com.mmg.manahub.feature.auth.domain.usecase.UpdateNicknameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.regex.Pattern
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val signInWithEmailUseCase: SignInWithEmailUseCase,
    private val signUpWithEmailUseCase: SignUpWithEmailUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val signUpWithGoogleUseCase: SignUpWithGoogleUseCase,
    private val linkGoogleIdentityUseCase: LinkGoogleIdentityUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val getSessionState: GetSessionStateUseCase,
    private val resetPasswordUseCase: ResetPasswordUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val updateNicknameUseCase: UpdateNicknameUseCase,
    private val analyticsHelper: AnalyticsHelper,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** Global session state — observed from MainActivity for navigation routing. */
    val sessionState: StateFlow<SessionState> = getSessionState()

    init {
        viewModelScope.launch {
            sessionState.collect { state ->
                when (state) {
                    is SessionState.Authenticated -> {
                        analyticsHelper.setUserId(state.user.id)
                    }
                    is SessionState.Unauthenticated -> {
                        analyticsHelper.setUserId(null)
                    }
                    else -> Unit
                }
            }
        }
    }

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Guards against concurrent auth operations triggered by rapid double-taps. */
    private var authJob: Job? = null

    fun signInWithEmail(email: String, password: String) {
        val validationError = validateEmailAndPassword(email, password, isSignUp = false)
        if (validationError != null) {
            _uiState.value = AuthUiState.Error(validationError)
            return
        }
        authJob?.cancel()
        authJob = viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            _uiState.value = when (val result = signInWithEmailUseCase(email.trim(), password)) {
                is AuthResult.Success -> AuthUiState.Success
                is AuthResult.Error -> AuthUiState.Error(result.error.toUiMessage())
            }
        }
    }

    fun signUpWithEmail(email: String, password: String, nickname: String, avatarUrl: String? = null) {
        val validationError = validateEmailAndPassword(email, password, isSignUp = true)
            ?: validateNickname(nickname)
        if (validationError != null) {
            _uiState.value = AuthUiState.Error(validationError)
            return
        }
        authJob?.cancel()
        authJob = viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            _uiState.value = when (val result = signUpWithEmailUseCase(email.trim(), password, nickname.trim(), avatarUrl)) {
                is AuthResult.Success -> AuthUiState.Success
                is AuthResult.Error -> when (result.error) {
                    is AuthError.EmailConfirmationRequired -> AuthUiState.EmailConfirmationSent
                    else -> AuthUiState.Error(result.error.toUiMessage())
                }
            }
        }
    }

    /**
     * Updates the authenticated user's nickname in Supabase.
     * Transitions to [AuthUiState.NicknameUpdated] on success.
     */
    fun updateNickname(nickname: String) {
        val validationError = validateNickname(nickname)
        if (validationError != null) {
            _uiState.value = AuthUiState.Error(validationError)
            return
        }
        authJob?.cancel()
        authJob = viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            _uiState.value = when (val result = updateNicknameUseCase(nickname.trim())) {
                is AuthResult.Success -> AuthUiState.NicknameUpdated
                is AuthResult.Error -> AuthUiState.Error(result.error.toUiMessage())
            }
        }
    }

    /**
     * Initiates Google Sign-In using Credential Manager on the main thread.
     * Generates a nonce pair (raw + SHA-256 hashed), presents the account picker,
     * then forwards the ID token to [SignInWithGoogleUseCase] for Supabase auth.
     *
     * Must be called from a UI event handler (e.g. button click) so that
     * [CredentialManager] has access to the foreground Activity context.
     */
    fun signInWithGoogle(activityContext: Context) {
        authJob?.cancel()
        authJob = viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val credentialManager = CredentialManager.create(activityContext)
                val rawNonce = generateNonce()
                val hashedNonce = hashNonce(rawNonce)

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
                    .setNonce(hashedNonce)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(activityContext, request)
                val credential = result.credential

                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    _uiState.value = when (val r = signInWithGoogleUseCase(idToken, rawNonce)) {
                        is AuthResult.Success -> AuthUiState.Success
                        is AuthResult.Error -> when (val err = r.error) {
                            // Special case: OAuth succeeded but no ManaHub profile exists.
                            // Signal the UI to switch to the Create Account tab and pre-fill the email.
                            is AuthError.NoProfileFound -> AuthUiState.GoogleSignInNoProfile(err.email)
                            // Email conflict: the Google email already exists as an email/password
                            // account. Store the pending token data so the user can link accounts
                            // by entering their password — no need to re-launch the Google picker.
                            is AuthError.GoogleEmailConflict -> AuthUiState.GoogleEmailConflictLinking(
                                email = err.email,
                                pendingIdToken = err.pendingIdToken,
                                pendingNonce = err.pendingNonce,
                            )
                            else -> AuthUiState.Error(err.toUiMessage())
                        }
                    }
                } else {
                    _uiState.value = AuthUiState.Error(appContext.getString(R.string.auth_error_credential_unsupported))
                }
            } catch (e: GetCredentialException) {
                _uiState.value = AuthUiState.Error(appContext.getString(R.string.auth_error_google_cancelled))
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(appContext.getString(R.string.auth_error_google_failed))
            }
        }
    }

    /**
     * Initiates Google Sign-Up using Credential Manager on the main thread.
     * Generates a nonce pair (raw + SHA-256 hashed), presents the account picker,
     * then forwards the ID token to [SignUpWithGoogleUseCase] for Supabase auth.
     *
     * @param nickname The nickname entered by the user on the sign-up tab.
     * @param avatarUrl The avatar URL selected by the user.
     *
     * Must be called from a UI event handler (e.g. button click) so that
     * [CredentialManager] has access to the foreground Activity context.
     */
    fun signUpWithGoogle(activityContext: Context, nickname: String, avatarUrl: String?) {
        val validationError = validateNickname(nickname)
        if (validationError != null) {
            _uiState.value = AuthUiState.Error(validationError)
            return
        }
        authJob?.cancel()
        authJob = viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val credentialManager = CredentialManager.create(activityContext)
                val rawNonce = generateNonce()
                val hashedNonce = hashNonce(rawNonce)

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
                    .setNonce(hashedNonce)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(activityContext, request)
                val credential = result.credential

                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    _uiState.value = when (val r = signUpWithGoogleUseCase(idToken, rawNonce, nickname, avatarUrl)) {
                        is AuthResult.Success -> AuthUiState.Success
                        is AuthResult.Error -> AuthUiState.Error(r.error.toUiMessage())
                    }
                } else {
                    _uiState.value = AuthUiState.Error(appContext.getString(R.string.auth_error_credential_unsupported))
                }
            } catch (e: GetCredentialException) {
                _uiState.value = AuthUiState.Error(appContext.getString(R.string.auth_error_google_cancelled))
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(appContext.getString(R.string.auth_error_google_failed))
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            signOutUseCase()
            // sessionState will emit Unauthenticated automatically via the SDK Flow
            _uiState.value = AuthUiState.Idle
        }
    }

    /**
     * Links the pending Google identity to the user's existing email/password account.
     *
     * Reads the stored [pendingIdToken] and [pendingNonce] from the current
     * [AuthUiState.GoogleEmailConflictLinking] state (set when the original Google Sign-In
     * returned a 422 conflict). The user-entered [password] is used to verify ownership
     * of the account before the link is performed.
     *
     * On success the state transitions to [AuthUiState.Success] and the UI dismisses
     * the linking dialog and navigates to HomeScreen.
     *
     * @param password The password the user entered in the account-linking dialog.
     */
    fun linkGoogleIdentity(password: String) {
        val linkingState = uiState.value as? AuthUiState.GoogleEmailConflictLinking ?: return
        authJob?.cancel()
        authJob = viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            _uiState.value = when (val result = linkGoogleIdentityUseCase(
                email = linkingState.email,
                password = password,
                pendingIdToken = linkingState.pendingIdToken,
                pendingNonce = linkingState.pendingNonce,
            )) {
                is AuthResult.Success -> AuthUiState.Success
                is AuthResult.Error -> AuthUiState.Error(result.error.toUiMessage())
            }
        }
    }

    /**
     * Sends a password-reset link to [email].
     * Transitions to [AuthUiState.ResetSent] on success or [AuthUiState.Error] on failure.
     */
    fun resetPassword(email: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || !EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
            _uiState.value = AuthUiState.Error(appContext.getString(R.string.auth_error_invalid_email))
            return
        }
        authJob?.cancel()
        authJob = viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            _uiState.value = when (val result = resetPasswordUseCase(trimmedEmail)) {
                is AuthResult.Success -> AuthUiState.ResetSent
                is AuthResult.Error -> AuthUiState.Error(result.error.toUiMessage())
            }
        }
    }

    /**
     * Permanently deletes the current user account and all associated data.
     * Transitions to [AuthUiState.AccountDeleted] on success.
     * The [sessionState] will emit [SessionState.Unauthenticated] automatically after deletion.
     */
    fun deleteAccount() {
        authJob?.cancel()
        authJob = viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            _uiState.value = when (val result = deleteAccountUseCase()) {
                is AuthResult.Success -> AuthUiState.AccountDeleted
                is AuthResult.Error -> AuthUiState.Error(result.error.toUiMessage())
            }
        }
    }

    /** Resets [uiState] back to [AuthUiState.Idle] so the sheet can be reused. */
    fun resetUiState() {
        _uiState.value = AuthUiState.Idle
    }

    // --- Helpers ---

    /**
     * Generates a cryptographically secure random nonce (32 bytes = 256 bits of entropy).
     * Uses SecureRandom instead of Kotlin's random() which relies on java.util.Random —
     * a predictable PRNG that must never be used for security-sensitive values.
     */
    private fun generateNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashNonce(rawNonce: String): String {
        val bytes = rawNonce.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)

        // Convert to lowercase hex string — Google's nonce verification accepts this format.
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    private fun AuthError.toUiMessage(): String = when (this) {
        is AuthError.InvalidCredentials -> appContext.getString(R.string.auth_error_invalid_credentials)
        is AuthError.EmailAlreadyInUse -> appContext.getString(R.string.auth_error_email_in_use)
        is AuthError.NetworkError -> appContext.getString(R.string.auth_error_network)
        is AuthError.SessionExpired -> appContext.getString(R.string.auth_error_session_expired)
        is AuthError.UserNotFound -> appContext.getString(R.string.auth_error_user_not_found)
        is AuthError.EmailConfirmationRequired -> appContext.getString(R.string.auth_email_confirmation_sent)
        is AuthError.NicknameInappropriate -> appContext.getString(R.string.auth_error_nickname_inappropriate)
        is AuthError.NicknameTooLong -> appContext.getString(R.string.auth_error_nickname_too_long)
        // GoogleEmailConflict normally transitions to GoogleEmailConflictLinking state and never
        // reaches toUiMessage. This fallback covers any unexpected path that bypasses that handling.
        is AuthError.GoogleEmailConflict -> appContext.getString(R.string.auth_error_google_email_conflict)
        // NoProfileFound is handled as GoogleSignInNoProfile state — this fallback
        // covers any unexpected path where it reaches toUiMessage directly.
        is AuthError.NoProfileFound -> appContext.getString(R.string.auth_error_no_profile_found)
        // Never expose raw server error messages to the user — they may leak internal
        // stack traces, table names, or constraint names from Supabase/Postgres.
        is AuthError.Unknown -> appContext.getString(R.string.auth_error_unknown)
    }

    /**
     * Validates email format and password strength before sending credentials to the server.
     * Returns a user-facing error string if invalid, or null if validation passes.
     *
     * Sign-up password requirements (must match [PasswordStrength] shown in the UI):
     *   - At least 8 characters
     *   - At least one lowercase letter
     *   - At least one uppercase letter
     *   - At least one digit
     *   - At least one symbol
     */
    private fun validateEmailAndPassword(
        email: String,
        password: String,
        isSignUp: Boolean,
    ): String? {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) return appContext.getString(R.string.auth_validation_email_required)
        if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
            return appContext.getString(R.string.auth_error_invalid_email)
        }
        if (password.isBlank()) return appContext.getString(R.string.auth_validation_password_required)
        if (isSignUp && !isPasswordStrong(password)) {
            return appContext.getString(R.string.auth_error_password_requirements)
        }
        return null
    }

    /**
     * Validates the nickname before sending it to the server.
     * Rules:
     * - Must not be blank.
     * - Must be 30 characters or fewer.
     * - Must contain only letters, digits, spaces, hyphens, underscores, and apostrophes.
     *   No leading or trailing spaces allowed.
     *
     * Returns a user-facing error string if invalid, or null if validation passes.
     */
    private fun validateNickname(nickname: String): String? {
        val trimmed = nickname.trim()
        if (trimmed.isBlank()) return appContext.getString(R.string.auth_error_nickname_required)
        if (trimmed.length > NICKNAME_MAX_LENGTH) return appContext.getString(R.string.auth_error_nickname_too_long)
        if (!NICKNAME_PATTERN.matches(trimmed)) return appContext.getString(R.string.auth_error_nickname_invalid)
        return null
    }

    companion object {
        /** Returns true when the password satisfies all sign-up requirements. */
        fun isPasswordStrong(password: String): Boolean =
            password.length >= 8 &&
            password.any { it.isLowerCase() } &&
            password.any { it.isUpperCase() } &&
            password.any { it.isDigit() } &&
            password.any { !it.isLetterOrDigit() }

        private const val NICKNAME_MAX_LENGTH = 30

        /** Alphanumeric, spaces, hyphens, underscores, apostrophes. No leading/trailing spaces. */
        private val NICKNAME_PATTERN = Regex("^[\\w\\s'\\-]{1,30}$")

        private val EMAIL_PATTERN: Pattern = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
        )
    }
}
