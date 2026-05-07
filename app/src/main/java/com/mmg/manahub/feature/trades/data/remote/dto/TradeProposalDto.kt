package com.mmg.manahub.feature.trades.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TradeProposalDto(
    val id: String,
    val status: String,
    @SerialName("proposer_id") val proposerId: String,
    @SerialName("receiver_id") val receiverId: String,
    @SerialName("parent_proposal_id") val parentProposalId: String? = null,
    @SerialName("root_proposal_id") val rootProposalId: String,
    @SerialName("proposal_version") val proposalVersion: Int,
    @SerialName("includes_review_collection_from_proposer") val includesReviewCollectionFromProposer: Boolean,
    @SerialName("includes_review_collection_from_receiver") val includesReviewCollectionFromReceiver: Boolean,
    @SerialName("proposer_marked_completed_at") val proposerMarkedCompletedAt: String? = null,
    @SerialName("receiver_marked_completed_at") val receiverMarkedCompletedAt: String? = null,
    @SerialName("cancellation_reason") val cancellationReason: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)
