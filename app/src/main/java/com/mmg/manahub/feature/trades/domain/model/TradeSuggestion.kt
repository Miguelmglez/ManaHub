package com.mmg.manahub.feature.trades.domain.model

data class TradeSuggestion(
    val wishingUserId: String,
    val offeringUserId: String,
    val cardId: String,
    val matchAnyVariant: Boolean,
    val userCardId: String,
    val offerFoil: Boolean,
    val offerCondition: String,
    val offerLanguage: String,
    val offerAltArt: Boolean,
    val suggestionType: String,
)
