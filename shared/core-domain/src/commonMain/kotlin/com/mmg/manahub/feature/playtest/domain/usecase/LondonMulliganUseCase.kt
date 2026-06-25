package com.mmg.manahub.feature.playtest.domain.usecase

import com.mmg.manahub.core.model.Card

/**
 * Applies the London Mulligan rule.
 *
 * London Mulligan procedure:
 *  1. Put ALL cards from the current hand back into the library.
 *  2. Shuffle the full library.
 *  3. Draw the same configured count (not reduced).
 *  4. After KEEPING, the player puts N cards on the bottom where N = mulligansUsed.
 *     — This "bottom-N" step is handled separately via [applyBottomN].
 *
 * The mulligan counter is incremented by 1 each time this use case is invoked.
 */
class LondonMulliganUseCase {

    /**
     * Reshuffles the current hand + library and redraws.
     *
     * @param currentHand The cards currently in the player's hand.
     * @param currentLibrary The remaining library cards.
     * @param drawCount The configured draw count (e.g. 7). Always draws this many.
     * @return Triple of (newHand, newLibrary, cardsReturnedToLibrary).
     */
    operator fun invoke(
        currentHand: List<Card>,
        currentLibrary: List<Card>,
        drawCount: Int,
    ): Pair<List<Card>, List<Card>> {
        // Combine hand + library, then shuffle.
        val combined = (currentHand + currentLibrary).toMutableList()
        combined.shuffle()

        // Draw configured count.
        val safeCount = drawCount.coerceAtMost(combined.size)
        val newHand = combined.take(safeCount)
        val newLibrary = combined.drop(safeCount)
        return newHand to newLibrary
    }

    /**
     * Applies the bottom-N step after the player decides to Keep with mulligans.
     *
     * Removes the selected cards from the hand and appends them to the BOTTOM
     * of the library. Returns the final kept hand and updated library.
     *
     * @param hand The cards in the current hand before bottom-N selection.
     * @param library The current library.
     * @param cardsToBottom The indices (into [hand]) of cards the player chose to bottom.
     *   Must have exactly [mulligansUsed] elements.
     * @return Pair of (finalHand, finalLibrary with bottomed cards at the bottom).
     */
    fun applyBottomN(
        hand: List<Card>,
        library: List<Card>,
        cardsToBottom: List<Int>,
    ): Pair<List<Card>, List<Card>> {
        val bottomedCards = cardsToBottom.map { hand[it] }
        val remainingHand = hand.filterIndexed { index, _ -> index !in cardsToBottom }
        val newLibrary = library + bottomedCards
        return remainingHand to newLibrary
    }
}
