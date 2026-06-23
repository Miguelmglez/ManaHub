package com.mmg.manahub.core.domain.model

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.OpenForTradeEntry
import com.mmg.manahub.core.model.WishlistEntry

/**
 * Represents a card slot inside a persisted deck with the full card data.
 *
 * Stays in `:app` (`core.domain.model`) because it references [Card], which is
 * not yet KMP-pure (gated by the tag-localization engine). The pure deck model
 * ([com.mmg.manahub.core.model.Deck] / [com.mmg.manahub.core.model.DeckSlot] /
 * [com.mmg.manahub.core.model.DeckWithCards]) lives in `:shared:core-model`.
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
 *
 * Stays in `:app` because it references the still-Android-coupled [Card],
 * [WishlistEntry] and [OpenForTradeEntry] models.
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
