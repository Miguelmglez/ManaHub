package com.mmg.manahub.feature.auth.domain.model

data class AuthUser(
    val id: String,          // UUID de auth.users
    val email: String?,
    val displayName: String?,
    val avatarUrl: String?,
    val provider: String     // "email" | "google"
)
