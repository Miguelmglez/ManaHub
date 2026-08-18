package com.mmg.manahub.app.navigation

import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.feature.auth.presentation.RECOVERY_MARKER_TTL_MS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit coverage for [shouldRouteToRecoveryScreen], the pure routing predicate backing
 * [AppNavGraph]'s password-recovery `LaunchedEffect` (CRITICAL/HIGH fix, 2026-08-17; hardened
 * 2026-08-18 with the pending-recovery marker — see `feature/auth/CLAUDE.md` and
 * `docs/plans/password-recovery-hardening-plan-2026-08-18.md`).
 *
 * This does NOT cover the `LaunchedEffect`/`NavController` wiring itself (no Compose test infra
 * exists for `AppNavGraph` today) — it covers the conditions the audited bugs hinged on:
 * (1) routing must fire purely off `SessionState` + the marker, independent of how/when either came
 * to be, (2) routing must not re-fire once the target destination is already current (re-entrancy),
 * and (3) `isRecoverySession` (the `amr` claim) alone is NOT sufficient — [shouldRouteToRecoveryScreen]
 * delegates to `isActiveRecoveryFlow`, which also requires a session-id-bound, time-bounded marker
 * (the fix for the signup-confirmation-ambiguity finding and the sticky-`amr` re-route loop).
 */
class AppNavGraphRoutingTest {

    private val sessionId = "session-abc-123"

    private fun recoveryUser(isRecoverySession: Boolean, sessionId: String? = this.sessionId) = AuthUser(
        id = "user-1",
        email = "user@example.com",
        nickname = "nick",
        gameTag = "#ABC123",
        avatarUrl = null,
        provider = "email",
        isRecoverySession = isRecoverySession,
        sessionId = sessionId,
    )

    private val now = 1_700_000_000_000L
    private fun validMarker(id: String = sessionId, markedAt: Long = now) = id to markedAt

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `routes when authenticated with a recovery session, a matching marker, and not already on the screen`() {
        val state = SessionState.Authenticated(recoveryUser(isRecoverySession = true))

        assertTrue(shouldRouteToRecoveryScreen(state, Screen.Home.route, validMarker(), now))
        assertTrue(shouldRouteToRecoveryScreen(state, null, validMarker(), now))
    }

    @Test
    fun `does not route when already on the reset password confirm screen (re-entrancy guard)`() {
        val state = SessionState.Authenticated(recoveryUser(isRecoverySession = true))

        assertFalse(
            shouldRouteToRecoveryScreen(state, Screen.ResetPasswordConfirm.route, validMarker(), now)
        )
    }

    @Test
    fun `does not route for a normal (non-recovery) authenticated session even with a marker present`() {
        val state = SessionState.Authenticated(recoveryUser(isRecoverySession = false))

        assertFalse(shouldRouteToRecoveryScreen(state, Screen.Home.route, validMarker(), now))
    }

    @Test
    fun `does not route while unauthenticated or loading`() {
        assertFalse(shouldRouteToRecoveryScreen(SessionState.Unauthenticated, null, validMarker(), now))
        assertFalse(shouldRouteToRecoveryScreen(SessionState.Loading, null, validMarker(), now))
    }

    @Test
    fun `re-emission of the same recovery state after routing once is a no-op`() {
        // Simulates AuthRepositoryImpl.sessionState's enrichment flow re-emitting the same
        // logical recovery-authenticated state (fast emit, then enriched emit) AFTER the first
        // emission already routed navController to Screen.ResetPasswordConfirm.
        val fastEmit = SessionState.Authenticated(recoveryUser(isRecoverySession = true))
        val enrichedEmit = SessionState.Authenticated(
            recoveryUser(isRecoverySession = true).copy(nickname = "enriched-nick")
        )

        assertTrue(shouldRouteToRecoveryScreen(fastEmit, Screen.Home.route, validMarker(), now))
        // After the first navigate(), currentDestination is now the target route.
        assertFalse(
            shouldRouteToRecoveryScreen(enrichedEmit, Screen.ResetPasswordConfirm.route, validMarker(), now)
        )
    }

    // ── Marker gate (2026-08-18 hardening) — S1/S2 regression coverage ─────────────

    @Test
    fun `does not route when amr indicates recovery but no marker is armed (signup-confirmation case)`() {
        // The S1 finding: a signup-confirmation session also satisfies isRecoverySession, but
        // MainActivity never arms a marker for it (only a genuine type=recovery deep link does).
        val state = SessionState.Authenticated(recoveryUser(isRecoverySession = true))

        assertFalse(shouldRouteToRecoveryScreen(state, Screen.Home.route, marker = null, now))
    }

    @Test
    fun `does not route when amr indicates recovery but no marker is armed (email-change-confirmation case, plan T5)`() {
        // password-recovery-hardening-plan-2026-08-18 §7.2 / plan task T5: structurally identical to
        // the signup-confirmation case above (amr: otp, authenticated, marker absent), but covered
        // under its own name deliberately -- email_change confirmation is the ONE of these
        // ambiguous-amr paths that actually reaches manahub://auth in production (signup
        // confirmation redirects to a web page, never the app deep link). Must gate shut exactly
        // like signup: MainActivity only arms a marker for a genuine type=recovery deep link.
        val state = SessionState.Authenticated(recoveryUser(isRecoverySession = true))

        assertFalse(shouldRouteToRecoveryScreen(state, Screen.Home.route, marker = null, now))
    }

    @Test
    fun `does not route when the marker's sessionId does not match the current session`() {
        val state = SessionState.Authenticated(recoveryUser(isRecoverySession = true, sessionId = sessionId))
        val markerForADifferentSession = validMarker(id = "some-other-session-id")

        assertFalse(shouldRouteToRecoveryScreen(state, Screen.Home.route, markerForADifferentSession, now))
    }

    @Test
    fun `does not route when the user session has no sessionId decoded at all`() {
        val state = SessionState.Authenticated(recoveryUser(isRecoverySession = true, sessionId = null))

        assertFalse(shouldRouteToRecoveryScreen(state, Screen.Home.route, validMarker(), now))
    }

    @Test
    fun `does not route when the marker has expired past its TTL`() {
        val state = SessionState.Authenticated(recoveryUser(isRecoverySession = true))
        val expiredMarker = validMarker(markedAt = now - RECOVERY_MARKER_TTL_MS - 1)

        assertFalse(shouldRouteToRecoveryScreen(state, Screen.Home.route, expiredMarker, now))
    }

    @Test
    fun `routes when the marker is exactly at the TTL boundary`() {
        val state = SessionState.Authenticated(recoveryUser(isRecoverySession = true))
        val boundaryMarker = validMarker(markedAt = now - RECOVERY_MARKER_TTL_MS)

        assertTrue(shouldRouteToRecoveryScreen(state, Screen.Home.route, boundaryMarker, now))
    }

    @Test
    fun `does not route when the marker's clock is ahead of now (clock skew, fails closed)`() {
        val state = SessionState.Authenticated(recoveryUser(isRecoverySession = true))
        val futureMarker = validMarker(markedAt = now + 1)

        assertFalse(shouldRouteToRecoveryScreen(state, Screen.Home.route, futureMarker, now))
    }

    @Test
    fun `does not route once the marker has been consumed after a successful reset (re-route-loop regression)`() {
        // S2 finding: amr stays true for the session's whole life (GoTrue re-emits it on every
        // refresh), so after a successful reset the ONLY thing that can stop this predicate from
        // re-satisfying on the next sessionState emission is the marker being cleared. This
        // simulates AuthRepositoryImpl.confirmPasswordReset's post-success marker clear.
        val state = SessionState.Authenticated(recoveryUser(isRecoverySession = true))

        assertTrue(shouldRouteToRecoveryScreen(state, Screen.Home.route, validMarker(), now))
        // Marker consumed; session state (in isolation) still looks identical.
        assertFalse(shouldRouteToRecoveryScreen(state, Screen.Home.route, marker = null, now))
    }
}
