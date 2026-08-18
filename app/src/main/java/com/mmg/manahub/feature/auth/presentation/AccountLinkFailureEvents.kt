package com.mmg.manahub.feature.auth.presentation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * One-shot, in-process signal for a failed `manahub://auth` deep-link callback — most notably the
 * "Link Google account" OAuth-redirect flow ([AuthViewModel.linkGoogleIdentityNative]).
 *
 * `MainActivity.handleSupabaseAuthDeepLink` catches every failure from
 * `supabaseClient.handleDeeplinks(intent)` (a malformed/token-less intent, GoTrue rejecting the
 * link, or the OAuth provider returning an error/denial redirect instead of a token-bearing one)
 * and, prior to this fix, only recorded a non-fatal — nothing in that catch block could reach
 * Compose state. Meanwhile [AccountManagementScreen]'s "Link Google account" button already
 * cleared its own loading state the moment the Custom Tab launched (see
 * [AuthUiState.GoogleIdentityLinkStarted]'s handling), so the button just silently re-enabled with
 * zero explanation to the user.
 *
 * This object lets `MainActivity` emit a fire-and-forget failure signal that
 * [AccountManagementScreen] collects (while it is the foregrounded screen) to show an error toast.
 *
 * Deliberately scoped to the common case only — the app foregrounded with a Custom Tab open. A
 * failure that arrives after process death (app killed while the Custom Tab is open, then
 * relaunched cold via the deep link) is NOT covered: that would require a durable,
 * cross-process-restart signal (e.g. a persisted flag), which is disproportionate complexity for
 * this UX-polish gap. `recordSafeNonFatal` in `MainActivity` still captures every failure for
 * diagnostics regardless of whether a collector is listening.
 *
 * The scheme/host (`manahub://auth`) is shared by three distinct callbacks (signup confirmation,
 * this Google-link OAuth redirect, and password recovery — see `MainActivity`'s KDoc), and a parse
 * failure carries no reliable signal distinguishing which one was in flight. The failure toast copy
 * is therefore intentionally generic ("Couldn't complete that action") rather than claiming
 * specifically that Google linking failed.
 */
object AccountLinkFailureEvents {
    private val _failures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val failures: SharedFlow<Unit> = _failures.asSharedFlow()

    /** Emits a failure signal. Safe to call with no active collector — buffered, never suspends. */
    fun notifyDeepLinkFailed() {
        _failures.tryEmit(Unit)
    }
}
