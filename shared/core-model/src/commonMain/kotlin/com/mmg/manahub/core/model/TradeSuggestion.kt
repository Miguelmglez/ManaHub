package com.mmg.manahub.core.model

data class TradeSuggestion(
    val wishingUserId: String,
    val offeringUserId: String,
    val cardId: String,
    val matchAnyVariant: Boolean,
    val userCardId: String,
    val offerFoil: Boolean,
    val offerCondition: String,
    val offerLanguage: String,
    val suggestionType: String,
)
