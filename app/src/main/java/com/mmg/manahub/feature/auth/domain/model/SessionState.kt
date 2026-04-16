package com.mmg.manahub.feature.auth.domain.model

sealed class SessionState {
    data object Loading : SessionState()
    data object Unauthenticated : SessionState()
    data class Authenticated(val user: AuthUser) : SessionState()
}
