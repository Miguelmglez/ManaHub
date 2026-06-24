package com.mmg.manahub.core.gamification.domain

/**
 * The recurrence window of a quest (ADR-002, Phase 2).
 *
 * Persisted as the enum NAME in `quest_instances.period` (a stable string — never persist ordinals).
 */
enum class QuestPeriod {
    /** Resets every local day. */
    DAILY,

    /** Resets every ISO week. */
    WEEKLY,
}

/**
 * The difficulty/accessibility band of a quest template, used by the deterministic generator to
 * guarantee a balanced selection (ADR-002 §9): at least two ACCESSIBLE quests and at most one
 * EXPLORATION quest per period.
 */
enum class QuestWeightClass {
    /** Low-effort, completable by any user in a normal session (e.g. play a game). */
    ACCESSIBLE,

    /** Requires a specific outcome or repetition (e.g. win a game, scan several cards). */
    STANDARD,

    /** Nudges the user toward a less-used feature (e.g. open Deck Doctor, run a tournament). */
    EXPLORATION,
}
