package com.mmg.manahub.core.domain.auth

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val error: AuthError) : AuthResult<Nothing>()
}
