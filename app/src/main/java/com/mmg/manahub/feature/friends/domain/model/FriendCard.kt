package com.mmg.manahub.feature.friends.domain.model

/**
 * Domain model representing a single card entry from a friend's list
 * (collection, wishlist, or trade offer).
 *
 * @property sourceList Origin list: 'collection', 'wishlist', or 'trade'.
 * @property scryfallId Scryfall UUID for the card printing.
 * @property name Card name.
 * @property imageNormal URL for the card's normal-size image.
 * @property setName Display name of the card's set.
 * @property rarity Rarity string: "common", "uncommon", "rare", "mythic", etc.
 * @property priceEur Market price in EUR, or null if unavailable.
 * @property priceUsd Market price in USD, or null if unavailable.
 * @property quantity Number of copies the friend owns in this list.
 * @property isFoil Whether this entry is a foil copy.
 * @property condition Condition descriptor (e.g. "NM", "LP"), or null.
 * @property language Language code (e.g. "en", "ja"), or null.
 */
data class FriendCard(
    val sourceList: String,
    val scryfallId: String,
    val name: String,
    val imageNormal: String?,
    val setName: String?,
    val rarity: String?,
    val priceEur: Double?,
    val priceUsd: Double?,
    val quantity: Int,
    val isFoil: Boolean,
    val condition: String?,
    val language: String?,
)
