package com.mmg.manahub.feature.trades.domain.model

import com.mmg.manahub.core.domain.model.Card

/** A card a user wants to acquire via trade. */
data class WishlistEntry(
    val id: String,
    val userId: String,
    val cardId: String,
    val matchAnyVariant: Boolean,
    val isFoil: Boolean?,
    val condition: String?,
    val language: String?,
    val isAltArt: Boolean?,
    val createdAt: Long,
    val card: Card? = null,
)
