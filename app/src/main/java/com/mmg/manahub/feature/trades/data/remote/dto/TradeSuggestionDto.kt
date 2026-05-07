package com.mmg.manahub.feature.trades.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TradeSuggestionDto(
    @SerialName("wishing_user_id") val wishingUserId: String,
    @SerialName("offering_user_id") val offeringUserId: String,
    @SerialName("card_id") val cardId: String,
    @SerialName("match_any_variant") val matchAnyVariant: Boolean,
    @SerialName("user_card_id") val userCardId: String,
    @SerialName("offer_foil") val offerFoil: Boolean,
    @SerialName("offer_condition") val offerCondition: String,
    @SerialName("offer_language") val offerLanguage: String,
    @SerialName("offer_alt_art") val offerAltArt: Boolean,
    @SerialName("suggestion_type") val suggestionType: String,
)
