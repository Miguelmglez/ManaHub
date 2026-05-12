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
 * @param profileCompleted Whether the user has finished the onboarding flow and chosen a nickname.
 *   The Supabase trigger [handle_new_user] creates the profile row with this field set to FALSE
 *   for every new auth.users entry (including Google OAuth). It is set to TRUE only after the
 *   user explicitly completes sign-up via the [complete_user_profile] RPC.
 */
data class AuthUser(
    val id: String,
    val email: String?,
    val nickname: String?,
    val gameTag: String?,
    val avatarUrl: String?,
    val provider: String,
    val profileCompleted: Boolean = false,
)
