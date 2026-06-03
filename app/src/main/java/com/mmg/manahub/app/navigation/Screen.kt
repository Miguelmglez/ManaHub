package com.mmg.manahub.app.navigation

import android.net.Uri

sealed class Screen(val route: String) {

    // ── Root ─────────────────────────────────────────────────────────────────
    object Splash : Screen("splash")

    // ── Collection (bottom tab 1) ────────────────────────────────────────────
    /** Bottom-tab root — also hosts the Cards/Decks sub-tab row. */
    object Collection : Screen("collection")
    object CollectionAddCard  : Screen("collection/add")
    object CollectionScanner  : Screen("collection/scanner")
    object CollectionCardDetail : Screen("collection/detail/{scryfallId}") {
        fun createRoute(scryfallId: String) = "collection/detail/${Uri.encode(scryfallId)}"
    }

    // ── Decks (sub-section of Collection) ────────────────────────────────────
    object DeckList    : Screen("collection/decks")
    object DeckDetail  : Screen("collection/deckmagic/{deckId}") {
        fun createRoute(deckId: String) = "collection/deckmagic/$deckId"
    }
    object DeckBuilder : Screen("collection/decks/builder")
    object DeckAddCards : Screen("collection/decks/{deckId}/add") {
        fun createRoute(deckId: String) = "collection/decks/$deckId/add"
    }
    object DeckImprovement : Screen("collection/decks/{deckId}/improvement") {
        fun createRoute(deckId: String) = "collection/decks/$deckId/improvement"
    }
    object Synergy : Screen("collection/decks/synergy")

    // ── Stats (bottom tab 2) ─────────────────────────────────────────────────
    object Stats    : Screen("stats")
    object Settings : Screen("settings")
    object TagDictionary : Screen("settings/tag_dictionary")

    // ── Profile (bottom tab 4) ───────────────────────────────────────────────
    object Profile : Screen("profile")
    object FriendsList : Screen("profile/friends")

    /**
     * Phantom screen that processes an incoming friend invite link.
     * Deep link patterns: `https://manahub.app/invite/{code}` and `manahub://invite/{code}`.
     */
    object FriendsInvite : Screen("friends/invite/{code}") {
        fun createRoute(code: String) = "friends/invite/$code"
    }

    /** Full-screen detail view for a specific friend, identified by their auth UUID. */
    object FriendDetail : Screen("friends/detail/{userId}") {
        fun createRoute(userId: String) = "friends/detail/$userId"
    }

    // ── Game flow (central FAB) ───────────────────────────────────────────────

    /**
     * Game setup screen. Supports an optional [joinCode] query parameter so that
     * deep-link join flows can pre-open the join sheet without a separate lobby screen.
     */
    object GameSetup : Screen("game/setup?joinCode={joinCode}") {
        /** Base route used for navigation when no join code is needed. */
        const val baseRoute = "game/setup"

        /** Builds a route that pre-fills the join sheet with [code]. */
        fun routeWithJoinCode(code: String) = "game/setup?joinCode=$code"
    }
    object GamePlay   : Screen("game/play/{mode}/{playerCount}") {
        fun createRoute(mode: String, playerCount: Int) = "game/play/$mode/$playerCount"
    }
    object GameResult : Screen("game/result")
    object GameSurvey : Screen("game/survey/{sessionId}?mode={mode}") {
        fun createRoute(sessionId: Long, mode: String = "COMPLETE") = "game/survey/$sessionId?mode=$mode"
    }

    // ── Tournament flow ───────────────────────────────────────────────────────
    object TournamentList   : Screen("tournament/list")
    object TournamentSetup  : Screen("tournament/setup")
    object TournamentDetail : Screen("tournament/{tournamentId}") {
        fun route(id: Long) = "tournament/$id"
    }

    // ── News ──────────────────────────────────────────────────────────────────
    object News : Screen("news")
    object NewsSourcesSettings : Screen("news_sources_settings")
    object NewsVideoPlayer : Screen("news/video/{videoId}?title={title}") {
        fun createRoute(videoId: String, title: String) =
            "news/video/${Uri.encode(videoId)}?title=${Uri.encode(title)}"
    }

    // ── Draft ─────────────────────────────────────────────────────────────────
    object Draft : Screen("draft")
    object DraftSetDetail : Screen("draft/{setCode}?setName={setName}&setIconUri={setIconUri}&setReleasedAt={setReleasedAt}") {
        fun createRoute(
            setCode: String,
            setName: String,
            setIconUri: String,
            setReleasedAt: String,
        ) = "draft/$setCode?setName=${Uri.encode(setName)}&setIconUri=${Uri.encode(setIconUri)}&setReleasedAt=${Uri.encode(setReleasedAt)}"
    }

    // ── Draft Simulator ──────────────────────────────────────────────────────

    /** Setup screen: choose draft mode and optional timer. Entry from DraftSetDetail. */
    object DraftSimSetup : Screen("draft/sim/setup/{setCode}") {
        fun createRoute(setCode: String) = "draft/sim/setup/$setCode"
    }

    /** Active drafting screen: pick cards pack by pack. */
    object DraftSimDrafting : Screen("draft/sim/drafting/{sessionId}") {
        fun createRoute(sessionId: String) = "draft/sim/drafting/$sessionId"
    }

    /** Result / deck-build screen after all packs are drafted. */
    object DraftSimResult : Screen("draft/sim/result/{sessionId}") {
        fun createRoute(sessionId: String) = "draft/sim/result/$sessionId"
    }

    // ── Online multiplayer lobby ──────────────────────────────────────────────

    /** Host lobby — configure and create a new online session. */
    object LobbyHost : Screen("online/lobby/host?mode={mode}&playerCount={playerCount}") {
        fun route(mode: String = "", playerCount: Int = 0) =
            "online/lobby/host?mode=$mode&playerCount=$playerCount"
    }

    /**
     * Join lobby — enter a 6-character code and wait for the session to start.
     * The [prefilledCode] parameter is optional (used by deep links and invite flows).
     */
    object LobbyJoin : Screen("online/lobby/join?code={code}") {
        /** Builds the route with an optional pre-filled join code. */
        fun route(code: String = "") = "online/lobby/join?code=$code"
    }

    // ── Deck Playtest ─────────────────────────────────────────────────────────

    /** Setup screen: choose draw count and on-the-play/draw before the first hand. */
    object PlaytestSetup : Screen("playtest/setup/{deckId}") {
        fun createRoute(deckId: String) = "playtest/setup/$deckId"
    }

    /**
     * Hand screen: draw, redraw, mulligan, keep, and save the test.
     * The [PlaytestSetup] object is passed in-memory from the setup screen —
     * no nav args needed for the full setup payload.
     */
    object PlaytestHand : Screen("playtest/hand/{deckId}") {
        fun createRoute(deckId: String) = "playtest/hand/$deckId"
    }

    // ── v2 stubs ─────────────────────────────────────────────────────────────
    object Puzzle : Screen("puzzle")

    // ── Trades (sub-section of Collection, also handles deep links) ───────────
    /** Root trades route — rendered as a sub-tab inside CollectionScreen. */
    object Trades : Screen("trades")

    /**
     * Deep link target for shared wishlist / open-for-trade lists.
     * App Link pattern: https://miguelmglez.github.io/list/{shareId}
     */
    object TradesSharedList : Screen("trades/shared/{shareId}") {
        fun createRoute(shareId: String) = "trades/shared/$shareId"
    }

    // ── Trade proposal flow ───────────────────────────────────────────────────
    object CreateTradeProposal : Screen(
        "trades/proposal/create/{receiverId}?parentProposalId={parentProposalId}&editingProposalId={editingProposalId}&rootProposalId={rootProposalId}"
    ) {
        fun createRoute(receiverId: String) = "trades/proposal/create/$receiverId"
        fun createCounterRoute(receiverId: String, parentProposalId: String, rootProposalId: String) =
            "trades/proposal/create/${Uri.encode(receiverId)}?parentProposalId=${Uri.encode(parentProposalId)}&rootProposalId=${Uri.encode(rootProposalId)}"
        fun createEditRoute(receiverId: String, editingProposalId: String, rootProposalId: String) =
            "trades/proposal/create/${Uri.encode(receiverId)}?editingProposalId=${Uri.encode(editingProposalId)}&rootProposalId=${Uri.encode(rootProposalId)}"
    }

    object TradeNegotiationDetail : Screen("trades/proposal/{proposalId}/thread/{rootProposalId}") {
        fun createRoute(proposalId: String, rootProposalId: String) =
            "trades/proposal/$proposalId/thread/$rootProposalId"
    }
}

