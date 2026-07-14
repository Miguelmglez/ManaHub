package com.mmg.manahub.core.gamification.domain.model

/**
 * Domain-level outcome of an atomic XP grant routed through the gamification repository.
 *
 * [applied] is `false` on a duplicate-key no-op (the ledger already contained the idempotency
 * key), in which case [previousLevel] and [newLevel] are equal. When `true`, [newLevel] reflects
 * the player's level AFTER the grant.
 *
 * This is the commonMain counterpart of `GamificationDao.GrantResult` (which stays in `androidMain`
 * because it is an inner class of the Room DAO). Repository implementations map from the DAO type
 * to this domain type before returning it to callers in shared code.
 */
data class GrantResult(
    /** `true` when the ledger row was inserted and the progression was advanced. */
    val applied: Boolean,
    /** Player level before this grant (or current level if [applied] is `false`). */
    val previousLevel: Int,
    /** Player level after this grant (equal to [previousLevel] if [applied] is `false`). */
    val newLevel: Int,
)
