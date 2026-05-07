package com.mmg.manahub.feature.trades.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenForTradeEntryDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_card_id") val userCardId: String,
    @SerialName("created_at") val createdAt: String,
    // fields joined from user_card_collection for display
    @SerialName("scryfall_id") val scryfallId: String? = null,
    @SerialName("is_foil") val isFoil: Boolean? = null,
    val condition: String? = null,
    val language: String? = null,
    @SerialName("is_alternative_art") val isAltArt: Boolean? = null,
)
