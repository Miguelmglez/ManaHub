package com.mmg.manahub.core.gamification.domain.model

/**
 * A single line in an XP grant breakdown (e.g. "+20 game logged", "+30 win").
 *
 * @param category which source bucket the XP came from.
 * @param amount XP granted for this line (already cap-adjusted).
 * @param label short English description for the UI (Phase 1+). PII-free.
 */
data class XpLineItem(
    val category: XpSourceCategory,
    val amount: Int,
    val label: String,
)

/**
 * The result of processing a single [com.mmg.manahub.core.gamification.domain.event.ProgressionEvent].
 *
 * Aggregates the XP outcome (from `XpGranter`) AND the achievement unlocks (from
 * `AchievementEvaluator`) for one event. Chunk B's GameResult progression strip renders [xpGranted] +
 * [breakdown] + [achievementUnlocks] + the level-up. A no-op (duplicate idempotency key, an event that
 * grants nothing AND unlocks nothing) is represented by [none].
 *
 * @param xpGranted total XP actually granted (0 if duplicate/no-op/capped to nothing). Note this is
 *   the XP from the *base event* grant; per-tier achievement XP is granted separately through the
 *   ledger and reflected in [achievementUnlocks].xpReward.
 * @param breakdown per-line breakdown of [xpGranted].
 * @param newLevel the level AFTER the grant, or null if no grant happened.
 * @param leveledUp true if the grant crossed at least one level boundary.
 * @param achievementUnlocks tiers unlocked while processing this event (empty when none).
 */
data class ProgressionOutcome(
    val xpGranted: Int,
    val breakdown: List<XpLineItem>,
    val newLevel: Int?,
    val leveledUp: Boolean,
    val achievementUnlocks: List<AchievementUnlock> = emptyList(),
) {
    /** True if this outcome carries anything the UI should surface (XP, a level-up, or an unlock). */
    val hasAnything: Boolean
        get() = xpGranted > 0 || leveledUp || achievementUnlocks.isNotEmpty()

    /** Returns a copy with [unlocks] folded into [achievementUnlocks]. */
    fun withAchievementUnlocks(unlocks: List<AchievementUnlock>): ProgressionOutcome =
        if (unlocks.isEmpty()) this else copy(achievementUnlocks = achievementUnlocks + unlocks)

    companion object {
        /** A no-op outcome: nothing was granted (duplicate event, capped out, or unmapped event). */
        val none: ProgressionOutcome = ProgressionOutcome(
            xpGranted = 0,
            breakdown = emptyList(),
            newLevel = null,
            leveledUp = false,
            achievementUnlocks = emptyList(),
        )
    }
}
