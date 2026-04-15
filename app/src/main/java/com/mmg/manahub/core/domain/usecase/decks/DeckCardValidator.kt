package com.mmg.manahub.core.domain.usecase.decks

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DeckFormat

/**
 * Centralized validation for adding cards to a deck.
 *
 * Encapsulates format-based copy-limit rules so that both the deck builder
 * and the deck detail screen enforce the same constraints without duplicating logic.
 */
object DeckCardValidator {

    /**
     * Result of a card addition check.
     */
    enum class AddResult {
        /** The card can be added without any issue. */
        ALLOWED,
        /** The card exceeds the format copy limit but may be acknowledged by the user. */
        OVER_LIMIT,
        /** The card is strictly blocked (e.g. unique-cards format already has a copy). */
        BLOCKED,
    }

    /**
     * Determines whether [card] can be added to a deck with the given [format]
     * considering [currentQuantity] copies already present.
     *
     * Basic lands are always [AddResult.ALLOWED] regardless of format restrictions.
     */
    fun canAddCard(
        card: Card,
        format: DeckFormat,
        currentQuantity: Int,
    ): AddResult {
        if (BasicLandCalculator.isBasicLand(card)) return AddResult.ALLOWED

        // Commander-style formats: only 1 copy of each non-basic card
        if (format.uniqueCards && currentQuantity >= 1) return AddResult.BLOCKED

        // Draft (maxCopies >= 99): unlimited
        if (format.maxCopies >= 99) return AddResult.ALLOWED

        // Standard-like formats: soft limit, allow but warn
        if (currentQuantity >= format.maxCopies) return AddResult.OVER_LIMIT

        return AddResult.ALLOWED
    }
}
