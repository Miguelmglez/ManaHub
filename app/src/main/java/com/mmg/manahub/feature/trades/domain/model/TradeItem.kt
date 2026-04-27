package com.mmg.manahub.feature.trades.domain.model

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
    val isAltArt: Boolean?,
    /** Scryfall card id. */
    val cardId: String,
    val isReviewCollectionPlaceholder: Boolean,
)
