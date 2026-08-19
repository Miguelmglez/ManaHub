package com.mmg.manahub.core

/**
 * Centralized compile-time UI feature flags for ManaHub.
 * Flip a flag to `true` to enable UI entry points for experimental or upcoming features.
 */
object FeatureFlags {

    /** Flags for the Decks feature and Deck Studio editor. */
    object Decks {
        /** Deck Studio "Suggestions" tab (inline Deck Doctor). */
        const val DECK_STUDIO_SUGGESTIONS_TAB_ENABLED = false

        /**
         * Deck Studio Suggestions/Analysis tab: Cuts, Adds (Motor A + outside-collection backstop),
         * Community (Motor B), and Similar-decks carousel. Independent of [DECK_STUDIO_SUGGESTIONS_TAB_ENABLED]
         * (which gates the whole tab) — this flag hides only these specific sub-sections while the tab's
         * plan chip + Health score/role-coverage/warnings stay visible. OFF while the Deck Analysis Engine v2
         * rewrite (docs/plans/deck-analysis-engine-v2-plan.md) is in progress; code stays compiled, not deleted.
         */
        const val DECK_STUDIO_SUGGESTIONS_ENGINE_ENABLED = false

        /** Deck Builder v2 wizard + generation + result screens. */
        const val DECK_BUILDER_V2_ENABLED = false

        /** Deck Builder v2 Discoveries clustering. */
        const val DISCOVERIES_V2_ENABLED = false

        /** Discoveries "Build this" and Combos "Use as seed" CTAs. */
        const val DISCOVERY_BUILD_HANDOFF_ENABLED = false
    }

    /** Flags for the Draft Simulator and Guides. */
    object Draft {
        /** The "Simulate Draft" button on the set-detail screen + the sim flow entry. */
        const val SIMULATOR_ENABLED = false
    }

    /** Flags for the Massive Add Cards feature. */
    object MassiveAdd {
        /** Massive Add Cards feature (multi-card input for collection/decks). */
        const val MASSIVE_CARDS_ENABLED = false
    }

    /** Flags for Online Multiplayer sessions and Tournaments. */
    object Online {
        /** Online multiplayer sessions: host/join a room, online tournaments. */
        const val ONLINE_SESSIONS_ENABLED = false
    }

    /** Flags for the Daily Puzzle feature. */
    object Puzzle {
        /** The Daily Puzzle Home widget and its associated navigation route. */
        const val PUZZLE_ENABLED = false
    }
}
