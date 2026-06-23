package com.mmg.manahub.core.model

/**
 * Represents a card slot inside a persisted deck with the full card data.
 *
 * Named [DeckSlotEntry] to avoid collision with the domain-level
 * `DeckCard` used by the deck builder.
 */
data class DeckSlotEntry(
    val scryfallId: String,
    val quantity: Int,
    val isSideboard: Boolean,
    val card: Card?,
)

/**
 * A candidate card row shown when adding cards to a deck.
 */
data class AddCardRow(
    val card: Card,
    val quantityInDeck: Int,
    val isOwned: Boolean,
    val availableQuantity: Int = 0,
    val wishlistEntry: WishlistEntry? = null,
    val offerEntry: OpenForTradeEntry? = null,
) {
    val uniqueKey: String get() = when {
        wishlistEntry != null -> "wishlist_${wishlistEntry.id}"
        offerEntry != null -> "offer_${offerEntry.id}"
        else -> "scryfall_${card.scryfallId}"
    }

    val isExactMatch: Boolean get() {
        val wish = wishlistEntry ?: return false
        val offer = offerEntry ?: return false
        if (wish.matchAnyVariant) return true
        return wish.isFoil == offer.isFoil &&
               (wish.condition == null || wish.condition == offer.condition) &&
               (wish.language == null || wish.language == offer.language)
    }
}
