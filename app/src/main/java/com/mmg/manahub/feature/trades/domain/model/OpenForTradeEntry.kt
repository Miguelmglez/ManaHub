package com.mmg.manahub.feature.trades.domain.model

import com.mmg.manahub.core.domain.model.Card

data class OpenForTradeEntry(
    val id: String,
    val userId: String,
    val userCardId: String,
    val scryfallId: String,
    val isFoil: Boolean,
    val condition: String,
    val language: String,
    val isAltArt: Boolean,
    val createdAt: Long,
    val card: Card? = null,
)
