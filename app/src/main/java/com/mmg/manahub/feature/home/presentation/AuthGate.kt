package com.mmg.manahub.feature.home.presentation

import com.mmg.manahub.core.domain.auth.SessionState

/**
 * The Home board's single source of truth for authentication.
 *
 * Every auth-dependent Home flow keys off this value, so the board can never disagree with itself
 * about whether the user is signed in. [Unknown] means the session has not resolved yet: account-
 * gated widgets must render neither their content nor their "create account" placeholder.
 */
sealed interface AuthGate {
    /** The session is still resolving; nothing auth-dependent may be rendered as definitive. */
    data object Unknown : AuthGate

    /** No account session (a guest/anonymous session counts as signed out). */
    data object SignedOut : AuthGate

    /** A real, non-anonymous account session for [userId]. */
    data class SignedIn(val userId: String) : AuthGate
}

/** The signed-in user id, or null when signed out or still unknown. */
val AuthGate.signedInUserId: String?
    get() = (this as? AuthGate.SignedIn)?.userId

/** Maps the domain session to the Home [AuthGate]; anonymous sessions are treated as signed out. */
fun SessionState.toAuthGate(): AuthGate = when (this) {
    SessionState.Loading -> AuthGate.Unknown
    SessionState.Unauthenticated -> AuthGate.SignedOut
    is SessionState.Authenticated ->
        if (user.isAnonymous) AuthGate.SignedOut else AuthGate.SignedIn(user.id)
}
