package com.mmg.manahub.feature.trades.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WishlistEntryDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("card_id") val cardId: String,
    val quantity: Int = 1,
    @SerialName("match_any_variant") val matchAnyVariant: Boolean,
    @SerialName("is_foil") val isFoil: Boolean? = null,
    val condition: String? = null,
    val language: String? = null,
    @SerialName("is_alt_art") val isAltArt: Boolean? = null,
    @SerialName("created_at") val createdAt: String,
)
