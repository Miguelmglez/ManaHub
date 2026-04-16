package com.mmg.manahub.feature.auth.domain.model

sealed class AuthError {
    data object InvalidCredentials : AuthError()
    data object EmailAlreadyInUse : AuthError()
    data object NetworkError : AuthError()
    data object UserNotFound : AuthError()
    data object SessionExpired : AuthError()
    /** Returned when Supabase requires email confirmation before the session is active. */
    data object EmailConfirmationRequired : AuthError()
    data class Unknown(val message: String?) : AuthError()
}
