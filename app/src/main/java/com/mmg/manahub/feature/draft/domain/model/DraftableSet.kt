package com.mmg.manahub.feature.draft.domain.model

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DraftSet

/**
 * A fully-resolved set ready for simulation: card pool, booster config, and tier-list ratings.
 * Assembled by [com.mmg.manahub.feature.draft.domain.repository.DraftSimRepository].
 */
data class DraftableSet(
    val set: DraftSet,
    val cards: List<Card>,
    val booster: BoosterConfig,
    /** Maps scryfallId → TierCard for O(1) rating lookup during bot scoring. */
    val ratings: Map<String, TierCard>,
)
