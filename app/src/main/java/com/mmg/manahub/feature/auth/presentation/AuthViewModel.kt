package com.mmg.manahub.feature.auth.presentation

import android.content.Context
import android.util.Patterns
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.mmg.manahub.BuildConfig
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val signInWithEmailUseCase: SignInWithEmailUseCase,
    private val signUpWithEmailUseCase: SignUpWithEmailUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val getSessionState: GetSessionStateUseCase,
    private val resetPasswordUseCase: ResetPasswordUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
) : ViewModel() {

    /** Global session state — observed from MainActivity for navigation routing. */
    val sessionState: StateFlow<SessionState> = getSessionState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionState.Loading
        )

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signInWithEmail(email: String, password: String) {
        val validationError = validateEmailAndPassword(email, password, isSignUp = false)
        if (validationError != null) {
            _uiState.value = AuthUiState.Error(validationError)
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            _uiState.value = when (val result = signInWithEmailUseCase(email.trim(), password)) {
                is AuthResult.Success -> AuthUiState.Success
                is AuthResult.Error -> AuthUiState.Error(result.error.toUiMessage())
            }
        }
    }

    fun signUpWithEmail(email: String, password: String) {
        val validationError = validateEmailAndPassword(email, password, isSignUp = true)
        if (validationError != null) {
            _uiState.value = AuthUiState.Error(validationError)
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            _uiState.value = when (val result = signUpWithEmailUseCase(email.trim(), password)) {
                is AuthResult.Success -> AuthUiState.Success
                is AuthResult.Error -> when (result.error) {
                    is AuthError.EmailConfirmationRequired -> AuthUiState.EmailConfirmationSent
                    else -> AuthUiState.Error(result.error.toUiMessage())
                }
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
    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val credentialManager = CredentialManager.create(context)
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

                val result = credentialManager.getCredential(context, request)
                val credential = result.credential

                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    _uiState.value = when (val r = signInWithGoogleUseCase(idToken, rawNonce)) {
                        is AuthResult.Success -> AuthUiState.Success
                        is AuthResult.Error -> AuthUiState.Error(r.error.toUiMessage())
                    }
                } else {
                    _uiState.value = AuthUiState.Error("Tipo de credencial no soportado")
                }
            } catch (e: GetCredentialException) {
                _uiState.value = AuthUiState.Error("Google Sign-In cancelado o no disponible")
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Error al iniciar sesión con Google")
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
     * Sends a password-reset link to [email].
     * Transitions to [AuthUiState.ResetSent] on success or [AuthUiState.Error] on failure.
     */
    fun resetPassword(email: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            _uiState.value = when (val result = resetPasswordUseCase(email)) {
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
        viewModelScope.launch {
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
        // Encode as hex — same character space as before, guaranteed uniform distribution
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashNonce(rawNonce: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(rawNonce.toByteArray())
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    private fun AuthError.toUiMessage(): String = when (this) {
        is AuthError.InvalidCredentials -> "Email o contraseña incorrectos"
        is AuthError.EmailAlreadyInUse -> "Este email ya está registrado"
        is AuthError.NetworkError -> "Sin conexión. Verifica tu red"
        is AuthError.SessionExpired -> "Sesión expirada. Inicia sesión de nuevo"
        is AuthError.UserNotFound -> "Usuario no encontrado"
        is AuthError.EmailConfirmationRequired -> "¡Revisa tu bandeja de entrada para confirmar tu email!"
        // Never expose raw server error messages to the user — they may leak internal
        // stack traces, table names, or constraint names from Supabase/Postgres.
        is AuthError.Unknown -> "Ha ocurrido un error inesperado. Inténtalo de nuevo"
    }

    /**
     * Validates email format and password strength before sending credentials to the server.
     * Returns a user-facing error string if invalid, or null if validation passes.
     *
     * Minimum password requirements (sign-up only):
     *   - At least 8 characters (NIST SP 800-63B minimum)
     * Email validation uses Android's built-in [Patterns.EMAIL_ADDRESS] matcher.
     */
    private fun validateEmailAndPassword(
        email: String,
        password: String,
        isSignUp: Boolean,
    ): String? {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) return "Introduce un email"
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            return "El formato del email no es válido"
        }
        if (password.isBlank()) return "Introduce una contraseña"
        if (isSignUp && password.length < 8) {
            return "La contraseña debe tener al menos 8 caracteres"
        }
        return null
    }
}
