package com.mmg.manahub.core.model

/**
 * A fully-resolved set ready for simulation: card pool, booster config, and tier-list ratings.
 * Assembled by [com.mmg.manahub.core.domain.repository.DraftSimRepository].
 */
data class DraftableSet(
    val set: DraftSet,
    val cards: List<Card>,
    val booster: BoosterConfig,
    /** Maps scryfallId → TierCard for O(1) rating lookup during bot scoring. */
    val ratings: Map<String, TierCard>,
)
