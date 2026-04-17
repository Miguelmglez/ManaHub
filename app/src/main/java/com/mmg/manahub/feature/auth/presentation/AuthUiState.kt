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
    data class Error(val message: String) : AuthUiState()
}
