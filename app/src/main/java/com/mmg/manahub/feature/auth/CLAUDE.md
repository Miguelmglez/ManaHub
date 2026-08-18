### Auth & Account Management

`AccountSection.kt` (Home/Profile card) is identity display + entry points only — **Sign Out and
Delete Account live in `AccountManagementScreen.kt`** (reachable via "Manage my account" from
`AccountSection` and Settings), not in `AccountSection` itself.

**Password change is protected by Supabase's `current_password` mechanism, not an email-nonce
gate.** The old `requestReauthentication()` → `SecurityCodeScreen` → `updatePassword(newPassword,
code)` flow was retired (2026-08-13, architecture pivot): live testing found GoTrue never actually
validated the `nonce` field on `updateUser` calls on this project (any code was accepted), while
the project's "Require current password when updating" dashboard setting IS enforced server-side.
`UpdatePasswordScreen` is now the SOLE screen for both "Change password" and "Set a password",
reached DIRECTLY from `AccountManagementScreen` — it collects the current password inline (shown
only when the account already has an email/password identity) and calls
`AuthRepository.updatePassword(newPassword, currentPassword)`. A wrong current password maps to
the distinct `AuthError.InvalidCurrentPassword` (GoTrue returns the SAME `invalid_credentials`
code/400 it uses for sign-in — distinguished by whether `currentPassword` was non-null in the
call). Email change still relies solely on Supabase's server-side "Secure Email Change"
(double-confirm via links to both old and new inbox) — `AuthRepository.confirmEmailUpdate(newEmail)`
takes no code and is unaffected by this change.

**"Set a password" (`currentPassword == null`) does NOT mean GoTrue skips the current-password
check — it means the account may already have a REAL password set server-side (2026-08-14
correction).** A Google-signup account has no `email` identity, but `signUpWithGoogle` already
called the `set-google-account-password` Edge Function to assign it a real password via the Admin
API — so a self-service `Auth.updateUser` call for it legitimately gets rejected by "Require
current password when updating" (surfaced as a generic 400, since this SDK version has no
`AuthErrorCode` for GoTrue's `current_password_required`). `AuthRepositoryImpl.updatePassword`
routes `currentPassword == null` through a DIFFERENT Edge Function, `set-account-password`
(`adminClient.auth.admin.updateUserById`, no `current_password` gate at all), instead of omitting
`currentPassword` from the same `Auth.updateUser` call. The `currentPassword != null` path is
unaffected — see `AuthRepository.updatePassword`'s KDoc for the full mechanism.

**`AuthUser.hasPassword` (2026-08-17 fix), NOT `identities.any { it.provider == "email" }` alone,
gates whether the UI offers "Change password" vs "Set a password".** The `set-account-password`
Edge Function (the "Set a password" flow above) is an Admin-API bypass — it never creates an
`email` identity server-side, so `hasEmailIdentity` can NEVER become true from that action no
matter how many times the user sets a password through it, permanently stranding the UI on "Set a
password" for that account. `user_profiles.has_password` (written `true` by `set-account-password`
only — `set-google-account-password`'s random password must keep it `false`) is threaded into
`AuthUser.hasPassword` via the same `sessionState` enrichment path every other field uses.
`AccountManagementScreen`'s `canChangePassword = hasEmailIdentity || user.hasPassword` is the real
gate for the "Change password" row / "Set a password" button / add-signin-method group visibility —
`hasEmailIdentity` alone is stale on its own for this specific account shape.

**Both `cancelPendingEmailChange()` and `setAccountPasswordViaEdgeFunction()` bypass the SDK's own
session-update path (a custom RPC / an Admin-API Edge Function) and therefore must explicitly
resync `sessionState` afterward — GoTrue's local session cache does not pick either change up on
its own (2026-08-17 fix).** `cancelPendingEmailChange` additionally calls
`supabaseAuth.retrieveUserForCurrentSession(updateSession = true)` (best-effort; a resync failure
does not turn the already-successful cancel into an `Error`) so `AuthUser.newEmail` clears in the
REAL session state, not just via `AccountManagementScreen`'s `emailChangeCancelledLocally` local UI
override (which stays, but purely as an instant-feedback optimization — no longer load-bearing for
correctness). Both now also call `profileRefreshSignal.tryEmit(Unit)` on success, mirroring every
other mutation in `AuthRepositoryImpl`, so `hasPassword`/other `user_profiles` fields stay in sync.

**"Cancel pending email change"**: `AccountManagementScreen`'s pending-email row shows a "Cancel
pending change" action (gated on `AuthUser.newEmail != null`) that calls
`AuthRepository.cancelPendingEmailChange()` → the `cancel_pending_email_change` RPC (verified
no-op-safe when nothing is pending), followed by a forced GoTrue resync — see the "bypass the SDK's
own session-update path" note above for the full mechanism. The screen ALSO still hides the pending
note instantly via the local `emailChangeCancelledLocally` override (now purely a UX optimization,
not the only fix, since the real session resync lands shortly after).

**A failed "Link Google account" OAuth-redirect deep link now surfaces a toast** instead of
silently re-enabling the button with no explanation. `MainActivity.handleSupabaseAuthDeepLink`'s
catch block emits into `AccountLinkFailureEvents` (a one-shot `SharedFlow`, `feature/auth/
presentation/AccountLinkFailureEvents.kt`), which `AccountManagementScreen` collects while
foregrounded. Scoped to the app-foregrounded case only — not process-death-safe by design (see
its KDoc for why that tradeoff is deliberate).

**`confirmPasswordReset`/`ResetPasswordConfirmScreen`/`AppNavGraph`'s recovery routing gate on
`isActiveRecoveryFlow(sessionState, marker, now)` (`feature.auth.presentation`, `AuthViewModel.kt`)
— `AuthUser.isRecoverySession` (the `amr` claim) ALONE is NOT sufficient, and never on
`SessionState.Authenticated` alone either.** A forged `manahub://auth?type=recovery` intent used to
reach this no-nonce password-set screen on any device with an active session (CRITICAL finding, fixed
2026-08-11). The `amr` match itself was corrected 2026-08-18: it accepts `"recovery"`, `"otp"`, AND
`"magiclink"` (GoTrue's own `AuthenticationMethod.IsRecovery()` set,
`github.com/supabase/auth`'s `internal/models/factor.go`) rather than only the literal `"recovery"`
string — a real recovery-link tap on this project tags the session `"otp"` (GoTrue's
`/verify?type=recovery` handler records the underlying OTP verification mechanism, not the request's
own `type` param), so the original single-string match failed closed against every genuine recovery
session.

**That widening turned out to be necessary but NOT sufficient (hardened same day, 2026-08-18 —
password-recovery-hardening-plan-2026-08-18.md).** Verified live against production data: GoTrue also
tags a **signup-email-confirmation** session `amr: otp` — the exact same value a genuine recovery-link
consumption gets (three of five `otp`-tagged rows in this project's `auth.mfa_amr_claims` were signup
confirmations, not recoveries). So `isRecoverySession` alone would route a brand-new user tapping
"Confirm your email" straight into "Set a new password", and pass the no-nonce
`confirmPasswordReset` gate. Separately, `amr` is session-lifetime-sticky (GoTrue re-emits the same
`amr` for a session's entire life, including after the password has already been changed via that
session), so gating on it alone also causes a re-route loop back to "Set a new password" after a
successful reset.

**The fix: an app-side, one-shot, process-death-durable marker, bound to the session by its
`session_id` JWT claim, required IN ADDITION to `amr`.** `MainActivity.handleSupabaseAuthDeepLink`
writes `UserPreferencesDataStore.setPendingRecoveryMarker(sessionId, markedAt)` ONLY when the deep
link's own `type=recovery` param is present AND `handleDeeplinks` actually imported a session from it
— never a substitute for the `amr` check, an ADDITION to it. `isActiveRecoveryFlow` requires:
`Authenticated` **AND** `isRecoverySession` **AND** a marker present **AND** `marker.sessionId ==
user.sessionId` **AND** marker age ≤ 15 minutes. The marker is consumed (cleared) on every exit —
success (inside `AuthRepositoryImpl.confirmPasswordReset`, together with a `SignOutScope.GLOBAL`
sign-out), Back/abandon (`AuthRepositoryImpl.abandonRecoverySession`, `SignOutScope.LOCAL` — not
GLOBAL, so abandoning on one device doesn't revoke sessions on others), or TTL expiry — which is what
stops the sticky-`amr` re-route loop: once the marker is gone, `isActiveRecoveryFlow` can never be
satisfied again by that same session, no matter how many more times its `amr` claim is re-emitted.
Any future deep-link-triggered sensitive action must gate on a JWT claim **plus** an app-side
one-shot marker proving the CURRENT session specifically came from that flow — a JWT claim alone is
not enough when GoTrue can legitimately tag more than one flow with the same claim value. See
`feedback_password_recovery_amr_gate`.

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

**"Set a password" (`setAccountPasswordViaEdgeFunction`) verifies its own `has_password` DB write
landed, not just that the Admin-API call returned HTTP 200 (2026-08-17 fix).** The
`set-account-password` Edge Function's `has_password = true` write to `user_profiles` is a
best-effort side-write logged server-side only — a write failure there was previously invisible: the
password change genuinely succeeded, but `AccountManagementScreen`'s `canChangePassword` gate would
stay stuck on "Set a password" forever with zero telemetry. `verifySetPasswordFlagLanded` awaits the
NEXT `sessionState` re-emission (triggered by the same `profileRefreshSignal.tryEmit(Unit)` the
success branch already fires) using **identity comparison (`!==`)** against the pre-call
`sessionState.value`, not structural equality — the enrichment `flow{}` always builds a brand-new
`SessionState.Authenticated` instance regardless of whether any field changed, so the exact mismatch
case this exists to catch (`hasPassword` still `false`, everything else unchanged) would never
satisfy a `!=` check. Bounded by a 3s `withTimeoutOrNull`; if it's still `false` after the refresh
lands, fires `recordNonFatal("account_mgmt_set_password_has_password_flag_stale")` — never an
`AuthResult.Error`, since the password update itself did succeed. The sibling `IOException` catch
also now fires `profileRefreshSignal.tryEmit(Unit)` before returning `NetworkError`: the server-side
write completes BEFORE the response streams back, so a drop after processing but before the client
reads it is a false negative that would otherwise strand the UI on the wrong flag for the rest of the
session.

**`setAccountPasswordViaEdgeFunction` also verifies the LOCAL SESSION ITSELF survived the
Admin-API write, not just the `has_password` DB flag (2026-08-17 production incident fix,
confirmed via `auth_logs` for a project test account).** GoTrue revokes the
account's active session server-side as a side effect of `admin.updateUserById` — the very next
`GET /user` 403s with `session_not_found`, and every authenticated call after that (including the
user's next "Change password" attempt) degrades further to `bad_jwt "missing sub claim"`. A plain
`profileRefreshSignal` emit cannot detect or fix this — it only re-triggers the `user_profiles`
enrichment fetch, which fails silently against the dead token (swallowed by `sessionState`'s own
try/catch), leaving a session that looks authenticated in memory but rejects every real call. The
fix calls `Auth.refreshCurrentSession()` (exchanges the refresh token — a genuine
`POST /token?grant_type=refresh_token`, distinct from `retrieveUserForCurrentSession`'s `GET
/user`) right after a successful write: if it succeeds, the session is genuinely repaired and the
flow proceeds exactly as before (`verifySetPasswordFlagLanded`, `AuthResult.Success`); if it ALSO
fails, the refresh token is revoked too and the session is unrecoverably dead, so the repository
records `recordNonFatal("account_mgmt_set_password_session_revoked")`, force-signs-out LOCALLY
(mirrors `deleteAccount`'s LOCAL-scope rationale — no network call with an already-dead token), and
returns `AuthError.PasswordUpdatedSessionRevoked` instead of a generic error. `AuthViewModel`'s
`toUiMessage()` maps this to `auth_error_password_set_session_revoked` ("Password set successfully.
For your security, please sign in again.") — the password change itself genuinely succeeded, so the
UI must never say "unexpected error" here. `UpdatePasswordScreen` needed NO new navigation code:
since the repository call returns `AuthResult.Error` (not `Success`) on this path,
`AuthUiState.PasswordUpdated` never fires and the screen stays put showing the reassurance message
inline via its existing `errorMessage` rendering (rather than a toast that would be lost by an
immediate auto-pop); `AccountManagementScreen`'s pre-existing, unconditional
`LaunchedEffect(sessionState)` (see below) already navigates the user out the moment they tap Back
into it, since `sessionState` is now `Unauthenticated`. Also defensive-backstop-fixed as part of the
same incident: `toAuthError()`'s three status-code fallback `when` blocks (`AuthRestException`,
`RestException`, `ResponseException`) now map a bare HTTP 403 to `AuthError.SessionExpired` instead
of falling through to `AuthError.Unknown` — this is what was actually rendering the original "An
unexpected error occurred" message on a normal "Change password" attempt against an
already-revoked session.

**`cancelPendingEmailChange`'s `retrieveUserForCurrentSession` resync failure uses
`recordSafeNonFatal`, not `recordNonFatal`** — the exception originates from an external SDK/network
call, so its message may carry sensitive data (matches `CrashlyticsHelper.kt`'s own documented
distinction). The explicit `profileRefreshSignal.tryEmit(Unit)` right after is now skipped when the
resync itself already succeeded (fired only as a fallback on resync failure) — a successful
`updateSession = true` resync updates `sessionStatus`, which `sessionState`'s `combine()` reacts to by
re-running the SAME `user_profiles` enrichment fetch that signal exists to trigger, so firing both
was a redundant round-trip that very likely cancelled and re-issued the in-flight fetch.

**`UpdatePasswordScreen`'s `requireCurrentPassword == true` branch has a "Forgot your password?" link**
(2026-08-17 fix) — before this, a Google-only user who used "Set a password" once and then forgot it
had no escape from this screen except signing out and going through the login screen's own reset
flow. It reuses `AuthViewModel.resetPassword` (the same action `LoginSheet`'s forgot-password dialog
calls) but skips asking for the email since the screen already knows it from the authenticated
session (`AuthViewModel.sessionState`). Since `AuthUiState` is a single shared state across every
`AuthViewModel` action, `ResetSent` is reset back to `Idle` immediately after showing its toast so it
never lingers to interfere with the screen's own `updatePassword` action's `isLoading`/error read.
Not shown in the "Set a password" branch — there is no current password to forget there.

**`user_profiles.has_password` is fetched via a self-scoped RPC (`get_my_has_password`), NEVER via
`UserProfileClient.fetchProfile`'s direct table select (2026-08-17 security fix).** The column was
briefly granted `SELECT` to `authenticated` **cross-user** — any user could read any OTHER user's
`has_password` (a metadata leak with no legitimate use) — because `fetchProfile`'s default `select`
listed it directly. Fixed by mirroring the existing `get_my_referral_code` pattern on this same
table (`FriendRemoteDataSource.getMyReferralCode`): `UserProfileClient.getMyHasPassword()` calls the
`get_my_has_password` RPC (`SECURITY DEFINER`, scoped to `(select auth.uid())` server-side), and
`UserProfileDataSource.fetchHasPassword(userId)` wraps it non-fatally (defaults `false` on any
failure — this is a best-effort UI-gating signal, not a security boundary). `UserProfileDto` no
longer carries a `hasPassword` field at all; the RPC has its own minimal `HasPasswordDto` response
type. `AuthRepositoryImpl.sessionState`'s enrichment block is the ONLY call site that queries the
live value — it makes a second call to `fetchHasPassword` right after `fetchUserProfile` succeeds
and merges the result into the enriched `AuthUser`. `UserProfileDataSource.upsertUserProfile` and
`completeUserProfile` both simply preserve the incoming `user.hasPassword` instead (both are
always brand-new-or-fresh-signup paths, so `false` is already correct there — consistent with the
existing invariant that only the `sessionState` enrichment path can ever set this field `true`, see
`AuthUser.hasPassword`'s KDoc). The matching Supabase-side `REVOKE SELECT (has_password) ON
public.user_profiles FROM authenticated` could not be re-applied until this client fix shipped (a
revoke first would have broken every user's profile fetch — PostgREST rejects the whole query if
any selected column lacks a grant) — see `feedback_column_revoke_must_grep_client_selects` for the
backend half of this story.

**Routing to `Screen.ResetPasswordConfirm` is owned ENTIRELY by `AppNavGraph`'s reactive
`LaunchedEffect(recoverySessionState)`, not by `MainActivity` or `PushDeeplinkRouter` at all
(2026-08-17 CRITICAL fix, hardened 2026-08-18 — corrects the paragraph this replaces).** `Auth.
parseFragmentAndImportSession` (auth-kt 3.5.0) calls `onFinish` (i.e. `handleDeeplinks`'s
`onSessionSuccess`) BEFORE `importSession(...)`, which is what actually updates
`sessionStatus`/`sessionState`. Navigating synchronously off that callback raced ahead of
`sessionState` reflecting the new recovery session — `ResetPasswordConfirmScreen` read a STALE
state on first composition and rendered the same "invalid/expired link" error the
`isRecoverySession` gate above shows for a genuinely forged intent, a false positive against a real
recovery link. The **first** fix (2026-08-17) had `MainActivity` wait, bounded by
`withTimeoutOrNull(3_000)`, on `AuthRepository.sessionState.first { it is Authenticated &&
it.user.isRecoverySession }` before enqueueing `PushDeeplinkRouter.enqueue("manahub://auth/recovery")`.
That closed the visible race but reopened a NARROWER version of the same bug: `handleDeeplinks`
persists the recovery session (Keystore-backed `SessionManager`, `autoLoadFromStorage = true`)
BEFORE that wait coroutine even starts — so if the process died mid-wait (e.g. the user backgrounds
the app right after tapping the link), the coroutine died with it, nothing was ever enqueued, yet
the recovery session survived in storage; on relaunch `MainActivity` never sees the original
`manahub://auth` intent again, so nothing routes the now-fully-recovery-authenticated user anywhere
— same end state (stranded on Home, no "set new password" form), different trigger.

**The fix (2026-08-18): `AppNavGraph` resolves `AuthRepository` via `koinInject()` and runs
`LaunchedEffect(recoverySessionState) { if (state is Authenticated && state.user.isRecoverySession
&& navController.currentDestination?.route != Screen.ResetPasswordConfirm.route) navigate(...,
{ launchSingleTop = true }) }`.** This has NO timeout and NO dependency on `MainActivity`'s intent
handling, coroutine, or even the process incarnation that imported the session — whenever
`sessionState` resolves to a recovery-authenticated session, in ANY process (fresh deep-link tap,
cold start restoring an already-persisted recovery session, warm resume), routing fires. The
`currentDestination` check (plus `launchSingleTop`) is also the re-entrancy guard for a double-tapped
recovery link: `AuthRepositoryImpl.sessionState`'s enrichment flow can emit the same
recovery-authenticated state twice (fast emit + enriched emit), and this check makes every emission
after the first a structural no-op instead of pushing a duplicate back-stack entry.
`MainActivity.handleSupabaseAuthDeepLink` still calls `handleDeeplinks(intent)` unconditionally
(that import is still required) but no longer waits on or resolves `AuthRepository` at all — its
only remaining recovery-specific code is a `FirebaseCrashlytics.log("auth_deeplink_recovery_session_imported")`
breadcrumb inside the `onSessionSuccess` callback, purely for observability. `Screen.ResetPasswordConfirm`
still declares the `manahub://auth/recovery` deep link pattern, but it is now an inert, unused
secondary entry point — no code path enqueues it anymore.

This is a GENERAL rule for this feature: never navigate a security-gated screen directly off
`handleDeeplinks`'s `onSessionSuccess`, AND never gate that routing on an Activity's
intent/coroutine surviving to completion — always drive it off the app's own reactive session state
observed at the point where the screen is actually rendered. See
`feedback_supabase_deeplink_onsessionsuccess_race` for the full mechanism and history.

**Hardened 2026-08-18 (password-recovery-hardening-plan-2026-08-18.md): the `LaunchedEffect` above
now ALSO collects `UserPreferencesDataStore.pendingRecoveryMarkerFlow` and passes it through
`shouldRouteToRecoveryScreen` to `isActiveRecoveryFlow` — routing on `isRecoverySession` alone (as
originally shipped 2026-08-17) admits a signup-confirmation session too (see the amr section above).
This closed three remaining defects in the same session:**
- **S1 (signup-confirm hijack):** without the marker, a brand-new user confirming their email would
  have been routed into "Set a new password" and passed the `confirmPasswordReset` gate. Closed by
  requiring the marker (only ever armed by a genuine recovery deep link) in addition to `amr`.
- **S2 (sticky-`amr` re-route loop):** `amr` never turns false for a session's life, so gating on it
  alone kept re-routing an ALREADY-completed reset back to "Set a new password" on the next
  `sessionState` emission (token refresh, resume, enrichment re-emit). Closed structurally by the
  marker being one-shot/consumable — see the amr section above for the consumption points; do not
  attempt to re-solve this with more route-name special-casing in `shouldRouteToRecoveryScreen`.
- **S3 (`onBack` leaves an authenticated recovery session — the original user-reported bug):**
  `ResetPasswordConfirmScreen`'s `onBack` used to only pop navigation
  (`if (!navController.popBackStack()) { navigate(Home) }`), leaving the recovery session itself
  fully authenticated and active — the user could land on `ProfileScreen` (or wherever was
  underneath) fully signed in, having never set a password. `onBack` now calls
  `AuthRepositoryImpl.abandonRecoverySession()` (clears the marker, `SignOutScope.LOCAL` sign-out —
  deliberately LOCAL, not GLOBAL, so abandoning on this device doesn't revoke the user's sessions on
  their other devices) via a documented downcast of the interface-typed `koinInject<AuthRepository>()`
  already present in `AppNavGraph`, THEN unconditionally navigates to `Screen.Home.route` with the
  ENTIRE back stack cleared (`popUpTo(startDestinationId) { inclusive = true }`) — never merely
  `popBackStack()`, since that can land on a screen (like `ProfileScreen`) that assumes an
  authenticated context.
- **Bonus (S4, successful-reset session hygiene):** a successful reset used to leave the user on the
  SAME recovery session. `AuthRepositoryImpl.confirmPasswordReset` now clears the marker and signs
  out with `SignOutScope.GLOBAL` (deliberately GLOBAL here — a completed password change should
  invalidate every OTHER session too, and guarantees the next session carries a clean `amr:
  password`) BEFORE returning `Success`, so this is atomic with the mutation itself rather than
  dependent on `AppNavGraph`'s `onPasswordReset` UI callback surviving to run. That callback is now
  pure navigation (to `Screen.Home.route`, same clean-stack pattern as `onBack`) plus a
  `MagicToast` reusing the existing `auth_error_password_set_session_revoked` copy ("Password set
  successfully. For your security, please sign in again.") — deliberately reused rather than adding
  a new string resource, since the wording already fits this case exactly.

`abandonRecoverySession()` is deliberately NOT part of the `AuthRepository` interface — it is an
Android-deep-link-specific mechanism (the marker it clears is written by `MainActivity`'s Android
intent handling) with no web target/equivalent yet, so adding it to the shared interface would force
an unrelated no-op implementation onto `WebAuthRepository` purely to satisfy the compiler. If a web
equivalent of this recovery flow is ever built, design its own marker/abandon mechanism rather than
forcing this Android-specific one onto the shared interface.

→ memory: `project_account_management_overhaul`, `feedback_supabase_deeplink_onsessionsuccess_race`,
`feedback_password_recovery_amr_gate`
