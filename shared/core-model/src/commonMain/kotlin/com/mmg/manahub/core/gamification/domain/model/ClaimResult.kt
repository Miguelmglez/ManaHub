package com.mmg.manahub.core.gamification.domain.model

/**
 * The result of attempting to claim a completed quest's reward.
 */
sealed interface ClaimResult {
    /** XP was granted and the quest moved to CLAIMED. */
    data class Claimed(val xpAwarded: Int, val newLevel: Int, val leveledUp: Boolean) : ClaimResult

    /** The instance does not exist. */
    data object NotFound : ClaimResult

    /** The quest is not yet COMPLETED (still ACTIVE or already EXPIRED). */
    data object NotCompleted : ClaimResult

    /** The reward was already claimed (idempotent — no XP granted again). */
    data object AlreadyClaimed : ClaimResult
}
