package com.mmg.manahub.feature.auth.presentation

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data object Success : AuthUiState()
    /** Emitted after a password-reset email has been sent successfully. */
    data object ResetSent : AuthUiState()
    /** Emitted after sign-up when Supabase requires email confirmation before the session is active. */
    data object EmailConfirmationSent : AuthUiState()
    /** Emitted after the user's account has been permanently deleted. */
    data object AccountDeleted : AuthUiState()
    /** Emitted after [AuthViewModel.updateNickname] completes successfully. */
    data object NicknameUpdated : AuthUiState()
    /**
     * Emitted when a Google Sign-In attempt succeeds at the OAuth level but no ManaHub
     * profile exists for this Google account. The UI should switch to the Create Account
     * tab and optionally pre-fill the [googleEmail] field.
     */
    data class GoogleSignInNoProfile(val googleEmail: String?) : AuthUiState()
    /**
     * Emitted when a Google Sign-In attempt collides with an existing email/password account.
     * The UI should present a password prompt so the user can confirm their identity and
     * link the Google account to the existing ManaHub account.
     *
     * @param email          The email extracted from the Google ID token — shown to the user.
     * @param pendingIdToken The Google ID token to be used for linking after password confirmation.
     * @param pendingNonce   The raw nonce from the original Google Sign-In request.
     */
    data class GoogleEmailConflictLinking(
        val email: String,
        val pendingIdToken: String,
        val pendingNonce: String,
    ) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}
