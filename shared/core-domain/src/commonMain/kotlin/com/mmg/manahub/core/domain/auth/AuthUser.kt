package com.mmg.manahub.core.domain.auth

import kotlinx.datetime.Instant

/**
 * A single authentication identity linked to an [AuthUser]'s account (e.g. "email" or "google").
 * A user may carry more than one identity after using [AuthRepository.linkGoogleIdentity] or
 * [AuthRepository.linkGoogleIdentityNative] to attach a second provider to an existing account.
 *
 * @param identityId Stable identifier for this identity row. Pass this to
 *   [AuthRepository.unlinkIdentity] to remove the identity from the account.
 * @param provider Provider name for this identity: "email" or "google".
 * @param createdAt When this identity was linked to the account, or null if the timestamp could
 *   not be parsed.
 */
data class AuthIdentity(
    val identityId: String,
    val provider: String,
    val createdAt: Instant?,
)

/**
 * Domain representation of an authenticated user.
 *
 * @param id UUID from auth.users.
 * @param email The user's email address, may be null for some providers.
 * @param nickname User-chosen display name (max 30 chars). Falls back to email prefix if null.
 * @param gameTag Auto-generated server-side identifier (e.g. "#A3KX9Z"). Never editable by user.
 * @param avatarUrl URL of the user's avatar image, may be null.
 * @param provider Authentication provider: "email" or "google". Derived from
 *   `identities.firstOrNull()?.provider` by the repository mapper — kept as a top-level field
 *   (rather than requiring every call site to read [identities] instead) so existing consumers of
 *   this field keep compiling unchanged.
 * @param profileCompleted Whether the user has finished the onboarding flow and chosen a nickname.
 *   The Supabase trigger [handle_new_user] creates the profile row with this field set to FALSE
 *   for every new auth.users entry (including Google OAuth). It is set to TRUE only after the
 *   user explicitly completes sign-up via the [complete_user_profile] RPC.
 * @param isAnonymous True when this session was created via anonymous sign-in (Supabase anonymous
 *   auth). Anonymous users do not have a [user_profiles] row and cannot access account-gated
 *   features.
 * @param isRecoverySession True only when this session's JWT carries a `"recovery"` entry in its
 *   `amr` (Authentication Methods Reference) claim — i.e. it was established by exchanging a real
 *   "forgot password" recovery-link/OTP token, not a normal password/OAuth/anonymous sign-in.
 *   SECURITY: this is the ONLY reliable signal that a session is a genuine password-recovery
 *   session; `AuthRepository.confirmPasswordReset` (a no-nonce password change) MUST gate on this
 *   field rather than on [SessionState.Authenticated] alone, since the deep link that routes a
 *   user to that confirmation screen carries an attacker-controllable `type=recovery` marker (see
 *   `decodeAmrIncludesRecoveryClaim`'s KDoc in `core-common`). Defaults to `false` — every mapper
 *   that cannot cheaply derive this from the session's raw access token (e.g. the web target) is
 *   correct to leave it at the safe default rather than guess.
 * @param emailConfirmedAt When the user's email address was confirmed, or null if it has not been
 *   confirmed yet (ADR-003 server-side email confirmation).
 * @param createdAt When the underlying `auth.users` account was created.
 * @param newEmail The pending new email address from an in-progress "change email" request that
 *   has not yet been confirmed via both the old and new inbox links (Supabase's "Secure email
 *   change"), or null when no change is pending. Sourced from GoTrue's `UserInfo.newEmail`.
 * @param identities Every authentication provider linked to this account. Empty when the SDK
 *   could not resolve any identity (should not normally happen for an authenticated session).
 */
data class AuthUser(
    val id: String,
    val email: String?,
    val nickname: String?,
    val gameTag: String?,
    val avatarUrl: String?,
    val provider: String,
    val profileCompleted: Boolean = false,
    val isAnonymous: Boolean = false,
    val isRecoverySession: Boolean = false,
    val emailConfirmedAt: Instant? = null,
    val createdAt: Instant? = null,
    val newEmail: String? = null,
    val identities: List<AuthIdentity> = emptyList(),
)
