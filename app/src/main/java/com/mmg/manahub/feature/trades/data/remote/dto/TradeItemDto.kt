package com.mmg.manahub.feature.trades.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TradeItemDto(
    val id: String,
    @SerialName("trade_proposal_id") val tradeProposalId: String,
    @SerialName("from_user_id") val fromUserId: String,
    @SerialName("to_user_id") val toUserId: String,
    @SerialName("user_card_id_ref") val userCardIdRef: String? = null,
    val quantity: Int? = null,
    @SerialName("is_foil") val isFoil: Boolean? = null,
    val condition: String? = null,
    val language: String? = null,
    @SerialName("is_alt_art") val isAltArt: Boolean? = null,
    @SerialName("card_id") val cardId: String,
    @SerialName("is_review_collection_placeholder") val isReviewCollectionPlaceholder: Boolean = false,
)
