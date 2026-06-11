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
 * Returned by the engine even though no UI consumes it in Phase 0 — later phases add
 * achievement/quest/streak deltas to it and surface a celebration strip. A no-op (duplicate
 * idempotency key, or an event that grants nothing) is represented by [none].
 *
 * @param xpGranted total XP actually granted (0 if duplicate/no-op/capped to nothing).
 * @param breakdown per-line breakdown of [xpGranted].
 * @param newLevel the level AFTER the grant, or null if no grant happened.
 * @param leveledUp true if the grant crossed at least one level boundary.
 */
data class ProgressionOutcome(
    val xpGranted: Int,
    val breakdown: List<XpLineItem>,
    val newLevel: Int?,
    val leveledUp: Boolean,
) {
    companion object {
        /** A no-op outcome: nothing was granted (duplicate event, capped out, or unmapped event). */
        val none: ProgressionOutcome = ProgressionOutcome(
            xpGranted = 0,
            breakdown = emptyList(),
            newLevel = null,
            leveledUp = false,
        )
    }
}
