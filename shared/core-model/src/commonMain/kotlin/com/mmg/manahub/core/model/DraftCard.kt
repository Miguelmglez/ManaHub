package com.mmg.manahub.core.model

/**
 * A card inside a draft context: wraps the real [Card] with tier-list rating signals.
 * No color/rarity enums — mirrors whatever is in [Card.colors] and [Card.rarity].
 *
 * [pickOrderRank] comes from [TierCard.pickOrderRank]
 * (1 = first pick, higher = later); null when the card is not in the tier list.
 */
data class DraftCard(
    val card: Card,
    val pickOrderRank: Int? = null,
    val tierRating: String? = null,
    val isFoil: Boolean = false,
)
