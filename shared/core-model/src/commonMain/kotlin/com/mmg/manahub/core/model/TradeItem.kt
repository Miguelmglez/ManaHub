package com.mmg.manahub.core.model

/** A single card line within a [TradeProposal], moving from one user to another. */
data class TradeItem(
    val id: String,
    val tradeProposalId: String,
    val fromUserId: String,
    val toUserId: String,
    /** Original collection instance id; null when the item is a review-collection placeholder. */
    val userCardIdRef: String?,
    val quantity: Int?,
    val isFoil: Boolean?,
    val condition: String?,
    val language: String?,
    /** Scryfall card id. */
    val cardId: String,
    /** Display name resolved from local Room DB; empty string if card not in local cache. */
    val cardName: String = "",
    /** Art-crop image URL resolved from local Room DB; null if card not in local cache. */
    val imageUrl: String? = null,
    /** Type line resolved from local Room DB. */
    val typeLine: String? = null,
    /** Set code resolved from local Room DB. */
    val setCode: String? = null,
    /** Set name resolved from local Room DB. */
    val setName: String? = null,
    /** Rarity resolved from local Room DB. */
    val rarity: String? = null,
    /** Display price (foil price already selected when isFoil == true). */
    val priceUsd: Double? = null,
    val priceEur: Double? = null,
    val isReviewCollectionPlaceholder: Boolean,
)
