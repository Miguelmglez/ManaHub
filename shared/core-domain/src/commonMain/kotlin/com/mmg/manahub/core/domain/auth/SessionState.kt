package com.mmg.manahub.core.domain.auth

sealed class SessionState {
    data object Loading : SessionState()
    data object Unauthenticated : SessionState()
    data class Authenticated(val user: AuthUser) : SessionState()
}
