package com.mmg.manahub.core.domain.model
import com.mmg.manahub.core.model.Card

/** A card a user wants to acquire via trade. */
data class WishlistEntry(
    val id: String,
    val userId: String,
    val cardId: String,
    val quantity: Int = 1,
    val matchAnyVariant: Boolean,
    val isFoil: Boolean = false,
    val condition: String?,
    val language: String?,
    val createdAt: Long,
    val card: Card? = null,
)
