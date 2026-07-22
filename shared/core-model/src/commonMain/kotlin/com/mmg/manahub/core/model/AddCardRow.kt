package com.mmg.manahub.core.model

/**
 * Represents a card slot inside a persisted deck with the full card data.
 *
 * Named [DeckSlotEntry] to avoid collision with the domain-level
 * `DeckCard` used by the deck builder.
 *
 * @param source Deck Engine Unification plan, D4 (RUN 7b fix) -- mirrors [DeckSlot.source].
 *        Defaults to [DeckCardSource.USER] so every pre-existing 4-arg call site keeps compiling
 *        unchanged. Threading this through from [DeckSlot] is what lets
 *        `DeckStudioViewModel`'s quantity-adjustment call sites preserve a slot's WIZARD/SUGGESTION
 *        provenance instead of silently defaulting the repository write back to USER.
 */
data class DeckSlotEntry(
    val scryfallId: String,
    val quantity: Int,
    val isSideboard: Boolean,
    val card: Card?,
    val source: DeckCardSource = DeckCardSource.USER,
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
    /**
     * A key stable and unique enough to use as a lazy-list item key.
     *
     * [offerEntry] is checked before [wishlistEntry] on purpose: when a row represents a
     * trade match (both [offerEntry] and [wishlistEntry] set — see
     * `TradeProposalViewModel.computeProposerMatches`/`computeReceiverMatches`), the
     * `wishlistEntry` is the *best-matching* wish for that offer and can be the SAME wish
     * entry shared by several distinct offer variants (e.g. two of my open-for-trade copies,
     * foil and non-foil, both matching the friend's one non-variant-specific wishlist row).
     * Keying on `wishlistEntry.id` first collapsed those rows onto one key. `offerEntry` is
     * always the row's own distinguishing copy in both match-computation paths, so it must
     * win the priority order. Every other call site only ever sets one of the two fields, so
     * this reordering is a no-op for them.
     */
    val uniqueKey: String get() = when {
        offerEntry != null -> "offer_${offerEntry.id}"
        wishlistEntry != null -> "wishlist_${wishlistEntry.id}"
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
