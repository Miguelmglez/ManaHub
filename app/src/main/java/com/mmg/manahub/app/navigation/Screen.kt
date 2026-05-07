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
        fun createRoute(scryfallId: String) = "collection/detail/$scryfallId"
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

    // ── Game flow (central FAB) ───────────────────────────────────────────────
    object GameSetup  : Screen("game/setup")
    object GamePlay   : Screen("game/play/{mode}/{playerCount}") {
        fun createRoute(mode: String, playerCount: Int) = "game/play/$mode/$playerCount"
    }
    object GameResult : Screen("game/result")
    object GameSurvey : Screen("game/survey/{sessionId}") {
        fun createRoute(sessionId: Long) = "game/survey/$sessionId"
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

    // ── v2 stubs ─────────────────────────────────────────────────────────────
    object Puzzle : Screen("puzzle")

    // ── Trades (sub-section of Collection, also handles deep links) ───────────
    /** Root trades route — rendered as a sub-tab inside CollectionScreen. */
    object Trades : Screen("trades")

    /**
     * Deep link target for shared wishlist / open-for-trade lists.
     * App Link pattern: https://trades.manahub.app/list/{shareId}
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

