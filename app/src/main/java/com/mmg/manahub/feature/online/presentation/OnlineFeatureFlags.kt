package com.mmg.manahub.feature.online.presentation

/**
 * Compile-time UI feature flags for the Online Sessions feature. Flip [ONLINE_SESSIONS_ENABLED]
 * to `true` to re-expose online play. Hiding is UI-only — every underlying ViewModel/repository/
 * use case/composable (Lobby host/join, in-game sync, online tournaments) stays compiled and
 * intact; only the entry points into it are hidden. Mirrors the
 * [com.mmg.manahub.feature.decks.presentation.DeckFeatureFlags] pattern.
 *
 * Local/offline same-device play, local tournaments, and every other Game Setup flow are
 * UNAFFECTED by this flag — it gates ONLY the online-specific entry points (host/join a room,
 * host/join an online tournament, the `LobbyHost`/`LobbyJoin` redirect routes and the
 * `manahub://join/{code}` deep link).
 */
object OnlineFeatureFlags {
    /** Online multiplayer sessions: host/join a room from Game Setup, online tournaments, and the
     * `manahub://join/{code}` deep link. */
    const val ONLINE_SESSIONS_ENABLED = false
}
