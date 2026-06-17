package com.mmg.manahub.feature.home.presentation

/**
 * The set of customizable Quick Start shortcuts on the Home dashboard.
 *
 * Each entry carries a stable [persistedId] used to serialise the user's chosen
 * order in DataStore. The id is decoupled from the enum name so the enum can be
 * renamed without invalidating persisted preferences.
 *
 * Quick Start is local-first: the user can pick exactly four shortcuts without an
 * account, and the order is preserved on restart. Account-required shortcuts
 * (e.g. [FRIENDS], [TRADES]) still render but route to an account prompt when the
 * user is unauthenticated rather than failing.
 */
enum class QuickStartAction(val persistedId: String) {
    SCAN_CARD("scan_card"),
    CREATE_DECK("create_deck"),
    DRAFT_GUIDE("draft_guide"),
    SEARCH_CARD("search_card"),
    DECKS("decks"),
    NEWS("news"),
    STATS("stats"),
    FRIENDS("friends"),
    TRADES("trades"),
    SETTINGS("settings");

    companion object {
        /**
         * Default shortcut set for zero-data users: one tap to start a game, scan a
         * card, build a deck, or open the draft guide.
         */
        val defaults = listOf(SCAN_CARD, CREATE_DECK, DRAFT_GUIDE, STATS)

        /** Resolves a persisted id back to its action, or null if unknown/removed. */
        fun fromPersistedId(id: String): QuickStartAction? =
            entries.firstOrNull { it.persistedId == id }
    }
}
