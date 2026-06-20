package com.mmg.manahub.core.domain.model

data class OpenForTradeEntry(
    val id: String,
    val userId: String,
    val userCardId: String,
    val scryfallId: String,
    val quantity: Int,
    val isFoil: Boolean,
    val condition: String,
    val language: String,
    val createdAt: Long,
    val card: Card? = null,
)
