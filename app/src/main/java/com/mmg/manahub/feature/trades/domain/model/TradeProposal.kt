package com.mmg.manahub.feature.trades.domain.model

/** A versioned trade proposal between two users, containing a list of [TradeItem]s. */
data class TradeProposal(
    val id: String,
    val status: TradeStatus,
    val proposerId: String,
    val receiverId: String,
    /** Set when this proposal is a counter-offer to a previous one. */
    val parentProposalId: String?,
    /** The first proposal in the counter-offer chain; equals [id] for root proposals. */
    val rootProposalId: String,
    val proposalVersion: Int,
    val includesReviewCollectionFromProposer: Boolean,
    val includesReviewCollectionFromReceiver: Boolean,
    val proposerMarkedCompletedAt: Long?,
    val receiverMarkedCompletedAt: Long?,
    val cancellationReason: String?,
    val items: List<TradeItem>,
    val createdAt: Long,
    val updatedAt: Long,
)
