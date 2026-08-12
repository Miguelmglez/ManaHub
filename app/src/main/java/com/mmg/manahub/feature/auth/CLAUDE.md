### Auth & Account Management

`AccountSection.kt` (Home/Profile card) is identity display + entry points only — **Sign Out and
Delete Account live in `AccountManagementScreen.kt`** (reachable via "Manage my account" from
`AccountSection` and Settings), not in `AccountSection` itself.

**Reauthentication gate applies to password change ONLY, not email change.** Email change relies
solely on Supabase's server-side "Secure Email Change" (double-confirm via links to both old and new
inbox) — `AuthRepository.confirmEmailUpdate(newEmail)` takes no code. Password change has no
equivalent server-side protection, so it's gated behind `requestReauthentication()` → `SecurityCodeScreen`
→ `updatePassword(newPassword, code)`. Do not add the code gate back to email change, and do not
remove it from password change.

**`confirmPasswordReset`/`ResetPasswordConfirmScreen` (the "Forgot password" landing screen) gate on
`AuthUser.isRecoverySession`, decoded from the session JWT's `amr` claim (`SupabaseJwt.
decodeAmrIncludesRecoveryClaim`) — never on `SessionState.Authenticated` alone.** A forged
`manahub://auth?type=recovery` intent used to reach this no-nonce password-set screen on any device
with an active session (CRITICAL finding, fixed 2026-08-11). Any future deep-link-triggered sensitive
action must gate on a JWT claim, not on the intent's own query params — see `feedback_password_recovery_amr_gate`.

**Account Management screens must gate on `!user.isAnonymous`, not just `SessionState.Authenticated`.**
An anonymous guest session (auto-signed-in for Online Sessions) reaching "Link Google account" would
have fired Supabase's real anonymous→permanent identity-link primitive with no `user_profiles` row
ever created for it. `AccountSection`, `SettingsScreen`'s "Manage account" row, and
`AccountManagementScreen`'s own session guard all check this now — mirror
`HomeViewModel.isAuthenticatedFlow`'s exclusion exactly if you add another entry point.

**Sensitive-op notification emails** (password changed / email changed / sign-in method linked /
removed) go through a new Edge Function `send-account-notification` (GMX SMTP, port 465 — the only
outbound port Supabase Edge Functions allow — via `denomailer`), called fire-and-forget from
`AuthRepositoryImpl.notifyAccountEvent` after each successful op. Never let a notification failure
surface as if the underlying operation failed.

**Sign-in methods list**: `unlinkIdentity` is guarded client-side (`identities.size > 1` disables
Remove) AND server-side — GoTrue itself natively rejects removing a user's last identity
(`AuthErrorCode.SingleIdentityNotDeletable`, HTTP 422); no custom trigger was needed. Linking a Google
identity to an email/password account uses the SDK's real `linkIdentity` (OAuth-redirect via Custom
Tabs), distinct from the Credential-Manager ID-token flow used for normal Google sign-in — success has
no SDK callback, it's inferred reactively in `AuthRepositoryImpl.trackIdentityLinkEvents` by diffing
`sessionState`'s identity-provider set.

**Delete Account / Unlink Identity / Sign Out use a `pendingAction: String?` tag, not a plain
boolean**, to disable/spin only the button matching the in-flight action — `AuthUiState.Loading` is a
single shared state across every action in `AuthViewModel`, so a bare boolean would spinner the wrong
button.

→ memory: `project_account_management_overhaul`
