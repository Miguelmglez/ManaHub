package com.mmg.manahub.feature.trades.domain.model

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
)
