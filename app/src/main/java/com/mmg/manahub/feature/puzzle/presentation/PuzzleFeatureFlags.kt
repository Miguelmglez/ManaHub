package com.mmg.manahub.feature.puzzle.presentation

/**
 * Compile-time UI feature flag for the Daily Puzzle (ADR-006, Batch B2). The feature is HIDDEN for
 * the current release at the user's request: only the entry points are gated (the
 * [com.mmg.manahub.feature.home.presentation.HomeWidgetType.DAILY_PUZZLE] Home widget — both the
 * widget-gallery add row and an already-added board tile — and its route,
 * [com.mmg.manahub.app.navigation.Screen.DailyPuzzle], simply becomes unreachable as a result).
 * `PuzzleScreen`/`PuzzleViewModel`/`PuzzleRepositoryImpl`/`PuzzleKoinModule`/the nav `composable`
 * block/`core/gamification/engine/XpGranter` XP hookup all stay intact and compiled — this mirrors
 * the exact UI-only-hide pattern used for [com.mmg.manahub.feature.draft.presentation
 * .DraftFeatureFlags.SIMULATOR_ENABLED]. Flip [PUZZLE_ENABLED] back to `true` to restore the
 * feature. See `docs/hidden-features/daily-puzzle.md`.
 */
object PuzzleFeatureFlags {
    /** The Daily Puzzle Home widget (gallery entry + board tile) — the sole reachable door into
     * [com.mmg.manahub.app.navigation.Screen.DailyPuzzle]. */
    const val PUZZLE_ENABLED = false
}
