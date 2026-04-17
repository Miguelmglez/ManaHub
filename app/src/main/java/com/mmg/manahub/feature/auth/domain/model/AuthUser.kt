package com.mmg.manahub.feature.auth.domain.model

/**
 * Domain representation of an authenticated user.
 *
 * @param id UUID from auth.users.
 * @param email The user's email address, may be null for some providers.
 * @param nickname User-chosen display name (max 30 chars). Falls back to email prefix if null.
 * @param gameTag Auto-generated server-side identifier (e.g. "#A3KX9Z"). Never editable by user.
 * @param avatarUrl URL of the user's avatar image, may be null.
 * @param provider Authentication provider: "email" or "google".
 */
data class AuthUser(
    val id: String,
    val email: String?,
    val nickname: String?,
    val gameTag: String?,
    val avatarUrl: String?,
    val provider: String
)
