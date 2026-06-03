package com.mmg.manahub.feature.draft.domain.model

/**
 * The final built deck from a draft: 23 non-land picks + 17 basic lands.
 * Produced by [com.mmg.manahub.feature.draft.domain.engine.DraftDeckBuilder].
 */
data class DraftDeck(
    val mainboard: List<DraftCard>,
    /** Basic land slots with resolved scryfallIds; counts sum to 17. */
    val basics: List<BasicLandSlot>,
)

data class BasicLandSlot(
    val scryfallId: String,
    val name: String,
    val count: Int,
)
