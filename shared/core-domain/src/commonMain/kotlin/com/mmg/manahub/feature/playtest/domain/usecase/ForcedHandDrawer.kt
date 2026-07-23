package com.mmg.manahub.feature.playtest.domain.usecase

import com.mmg.manahub.core.model.Card

/**
 * Draws a hand of [count] cards from [shuffledPool], guaranteeing that every "Custom your hand"
 * forced card in [forced] is included (bounded by its own availability in the pool).
 *
 * This is a pure function — it does not mutate [shuffledPool]; the caller must already have
 * shuffled it (this function only partitions and takes, it never randomizes). It is the single
 * shared draw primitive behind every hand-building path so forced cards survive the initial draw,
 * a redraw, a London mulligan, and an instant Custom Hand re-apply identically:
 *  - [PlaytestHandViewModel.buildAndDraw] (initial draw + `onRedraw`).
 *  - [LondonMulliganUseCase.invoke] (after its own combine+shuffle step).
 *  - [PlaytestHandViewModel.applyCustomHandSelection] (instant re-apply on sheet confirm).
 *
 * Forced cards are placed at the FRONT of the returned hand, in [shuffledPool] iteration order.
 * This ordering is intentional, not incidental: it is what lets [computeProtectedIndices] identify
 * "which physical copies are protected from bottoming" via a simple scan, with no per-instance id
 * needed on [Card] during the MULLIGAN phase.
 *
 * @param shuffledPool The combined, already-shuffled pool to draw from (hand + library, or a fresh
 *   library on the very first draw).
 * @param count Total hand size to draw (the configured `drawCount`).
 * @param forced scryfallId → forced copy count. A card's forced count is capped by how many
 *   copies of it actually appear in [shuffledPool] — this function never invents copies.
 * @return Pair of (drawn hand, remaining pool as the new library).
 */
fun drawWithForced(
    shuffledPool: List<Card>,
    count: Int,
    forced: Map<String, Int>,
): Pair<List<Card>, List<Card>> {
    val forcedTaken = mutableMapOf<String, Int>()
    val forcedCards = mutableListOf<Card>()
    val rest = mutableListOf<Card>()
    for (card in shuffledPool) {
        val need = forced[card.scryfallId] ?: 0
        val takenSoFar = forcedTaken[card.scryfallId] ?: 0
        if (takenSoFar < need) {
            forcedCards += card
            forcedTaken[card.scryfallId] = takenSoFar + 1
        } else {
            rest += card
        }
    }
    val remainingSlots = (count - forcedCards.size).coerceAtLeast(0)
    val hand = forcedCards + rest.take(remainingSlots)
    val library = rest.drop(remainingSlots)
    return hand to library
}
