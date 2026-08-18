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
 * @param isRecoverySession True only when this session's JWT carries an `amr` (Authentication
 *   Methods Reference) entry whose `method` is `"recovery"`, `"otp"`, or `"magiclink"` — the exact
 *   set GoTrue's own server-side `AuthenticationMethod.IsRecovery()` (`github.com/supabase/auth`,
 *   `internal/models/factor.go`) treats as a genuine "forgot password" recovery session, as opposed
 *   to a normal password/OAuth/anonymous sign-in. In practice a real recovery-link tap on this
 *   project tags the session `"otp"` (GoTrue's `/verify?type=recovery` handler records the
 *   underlying OTP verification mechanism, not the literal `"recovery"` string), so matching only
 *   `"recovery"` would fail closed against every genuine recovery session — see
 *   `decodeAmrIndicatesRecoverySession`'s KDoc in `core-common` for the full citation.
 *
 *   SECURITY (corrected 2026-08-18 — an earlier version of this KDoc claimed this field ALONE was
 *   sufficient authorization; that claim is disproven): `"otp"` is also the value GoTrue tags a
 *   **signup-email-confirmation** session with — verified live against production data (three of
 *   five `otp`-tagged `auth.mfa_amr_claims` rows on this project were signup confirmations, not
 *   recoveries; see `docs/plans/password-recovery-hardening-plan-2026-08-18.md` §2.1). This field is
 *   a NECESSARY but NOT SUFFICIENT signal — `AuthRepository.confirmPasswordReset` (a no-nonce
 *   password change) MUST gate on this field **AND** a one-shot, app-side "a genuine recovery deep
 *   link produced THIS session" marker matched by [sessionId] (never on [SessionState.Authenticated]
 *   or this field alone), since the deep link that routes a user to that confirmation screen carries
 *   an attacker-controllable `type=recovery` marker AND a genuine signup-confirmation link produces
 *   a session indistinguishable from recovery by this field alone. Defaults to `false` — every
 *   mapper that cannot cheaply derive this from the session's raw access token (e.g. the web target)
 *   is correct to leave it at the safe default rather than guess.
 * @param sessionId The `session_id` claim from this session's JWT (identifies the underlying
 *   `auth.sessions` row; stable across token refreshes for the same session, different for every
 *   other session), or null when it could not be decoded. Used together with [isRecoverySession] to
 *   validate the app-side recovery marker described above — see `decodeSessionIdClaim`'s KDoc in
 *   `core-common`. Defaults to `null`; only the `sessionState` enrichment path (Android) currently
 *   populates it from the raw access token.
 * @param emailConfirmedAt When the user's email address was confirmed, or null if it has not been
 *   confirmed yet (ADR-003 server-side email confirmation).
 * @param createdAt When the underlying `auth.users` account was created.
 * @param newEmail The pending new email address from an in-progress "change email" request that
 *   has not yet been confirmed via both the old and new inbox links (Supabase's "Secure email
 *   change"), or null when no change is pending. Sourced from GoTrue's `UserInfo.newEmail`.
 * @param identities Every authentication provider linked to this account. Empty when the SDK
 *   could not resolve any identity (should not normally happen for an authenticated session).
 * @param hasPassword True when the account has a real, user-manageable password set — either via
 *   a genuine `email` identity or via the Admin-API-backed `set-account-password` Edge Function
 *   (a Google-only account that used "Set a password" from Account Management). Distinct from
 *   `identities.any { it.provider == "email" }`: that check is permanently unreachable for an
 *   account whose password was set via the Admin API bypass, since that path never creates an
 *   `email` identity server-side. Sourced from the `user_profiles.has_password` column (set by the
 *   `set-account-password` Edge Function only — NOT by `set-google-account-password`, whose random
 *   password the user never sees and which must keep this `false`). Defaults to `false`; only the
 *   `sessionState` enrichment path (which reads `user_profiles`) can set it `true`.
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
    val sessionId: String? = null,
    val emailConfirmedAt: Instant? = null,
    val createdAt: Instant? = null,
    val newEmail: String? = null,
    val identities: List<AuthIdentity> = emptyList(),
    val hasPassword: Boolean = false,
)
