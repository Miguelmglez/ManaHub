package com.mmg.magicfolder.app.navigation

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
    object DeckDetail  : Screen("collection/decks/{deckId}") {
        fun createRoute(deckId: Long) = "collection/decks/$deckId"
    }
    object DeckBuilder : Screen("collection/decks/builder")
    object DeckAddCards : Screen("collection/decks/{deckId}/add") {
        fun createRoute(deckId: Long) = "collection/decks/$deckId/add"
    }

    // ── Stats (bottom tab 2) ─────────────────────────────────────────────────
    object Stats    : Screen("stats")
    object Settings : Screen("settings")

    // ── Profile (bottom tab 4) ───────────────────────────────────────────────
    object Profile : Screen("profile")

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

    // ── v2 stubs ─────────────────────────────────────────────────────────────
    object Draft  : Screen("draft")
    object Puzzle : Screen("puzzle")
}
