package com.mmg.manahub.core.gamification.domain.catalog

import com.mmg.manahub.core.FeatureFlags

/**
 * Whether a catalog entry (achievement, quest template, unlockable) can currently be earned
 * (drift audit G-14/G-15). Unavailable entries are hidden from the UI and never evaluated, generated,
 * backfilled or granted; persisted progress for them is kept untouched.
 */
enum class CatalogAvailability {
    /** Earnable whenever gamification itself is available. */
    ALWAYS,

    /** Needs a local "this is me" tournament seat, which does not exist yet. */
    TOURNAMENT_LOCAL_SEAT,

    /** Follows the Daily Puzzle release flag. */
    DAILY_PUZZLE,
    ;

    /** True when entries with this availability may be shown, evaluated and granted. */
    val isAvailable: Boolean
        get() = when (this) {
            ALWAYS -> true
            TOURNAMENT_LOCAL_SEAT -> false
            DAILY_PUZZLE -> FeatureFlags.Puzzle.PUZZLE_ENABLED
        }
}
