package com.mmg.manahub.feature.playtest.domain.usecase

import com.mmg.manahub.core.domain.model.Card
import javax.inject.Inject

/**
 * Draws the top N cards from a given library.
 *
 * This is a pure function — it does not modify the library list; the caller
 * receives both the drawn hand and the remaining library separately.
 *
 * Used for:
 *   - Initial draw at the start of a playtest session.
 *   - Redraw after a full reshuffle (ephemeral "Nueva mano").
 *   - Redraw after a London mulligan (reshuffle + draw same count).
 */
class DrawHandUseCase @Inject constructor() {

    /**
     * @param library The current library in draw order (index 0 = top).
     * @param count Number of cards to draw.
     * @return Pair of (drawn hand, remaining library).
     */
    operator fun invoke(
        library: List<Card>,
        count: Int,
    ): Pair<List<Card>, List<Card>> {
        val safeCount = count.coerceAtMost(library.size)
        val hand = library.take(safeCount)
        val remaining = library.drop(safeCount)
        return hand to remaining
    }
}
