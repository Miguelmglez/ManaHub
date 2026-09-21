package com.mmg.manahub.core

/**
 * Centralized compile-time UI feature flags for ManaHub.
 * Flip a flag to `true` to enable UI entry points for experimental or upcoming features.
 */
object FeatureFlags {

    /** Flags for the Decks feature and Deck Studio editor. */
    object Decks {
        /** Deck Studio "Suggestions"/Analysis tab (inline Deck Doctor: score/pillars/findings +
         * "Decks like yours"). Deck Analysis Category Sections rework (W0, D3/D5): the old Motor
         * A/B "Adds"/"Cuts" suggestion engine and its own independent
         * `DECK_STUDIO_SUGGESTIONS_ENGINE_ENABLED` sub-flag were deleted end-to-end — the tab's
         * Analysis content (score ring, pillar tiles, findings, "Decks like yours") is now always
         * on whenever this flag is, no separate engine gate. */
        const val DECK_STUDIO_SUGGESTIONS_TAB_ENABLED = true

        /** Deck Builder v2 wizard + generation + result screens. Deck Wizard Commander v3 plan,
         * Phase 8: flipped on after the full verify gauntlet passed (harness v2 all 3 segments,
         * golden/calibration/corpus/skeleton suites byte-identical, persist() atomicity closed) --
         * see `docs/deck-wizard-state.md` for the durable record. */
        const val DECK_BUILDER_V2_ENABLED = true

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
