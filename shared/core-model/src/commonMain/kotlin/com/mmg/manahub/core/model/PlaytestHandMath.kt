package com.mmg.manahub.core.model

/**
 * Computes how many cards must be put on the bottom of the library at Keep time.
 *
 * Combines two independent sources of "bottoming pressure":
 *  1. The gap between [drawCount] (cards drawn) and [startCount] ("Cards to start the game") —
 *     e.g. drawing 10 but only wanting to start with 7 means 3 cards must go to the bottom even
 *     with zero mulligans.
 *  2. The classic London Mulligan rule: [mulligansUsed] cards must go to the bottom regardless of
 *     [startCount].
 *
 * The result is the MAX of the two (they are not additive — a mulligan already implies the player
 * is committing to bottom at least that many cards, which subsumes a smaller start-count gap), and
 * is always clamped to `[0, drawCount - 1]` so the kept hand can never drop below 1 card — this
 * clamp is the sole source of the ≥1-card floor invariant; callers must not re-derive it.
 *
 * Worked example (confirmed with product): drawCount=10, startCount=7, mulligansUsed=4 →
 * `max(10-7, 4).coerceIn(0, 9)` = `max(3, 4)` = 4 → final kept hand = 10 - 4 = 6 cards.
 *
 * @return The number of cards that must be selected in the bottom-N step, in `[0, drawCount - 1]`.
 */
fun computeRequiredBottomCount(drawCount: Int, startCount: Int, mulligansUsed: Int): Int =
    maxOf(drawCount - startCount, mulligansUsed).coerceIn(0, drawCount - 1)

/**
 * Locates which indices into [hand] correspond to "Custom your hand" forced copies, so the
 * bottom-N selector can refuse to bottom them.
 *
 * There is no per-instance id on a [Card] while in the MULLIGAN phase (unlike the PLAY-phase
 * [PlayCard.instanceId]) — duplicate copies of the same card are interchangeable, so protection is
 * computed by a single left-to-right scan rather than by a stored per-card flag: for each
 * scryfallId, the first `forced[scryfallId]` occurrences encountered are marked protected. This
 * relies on [drawWithForced] always placing forced copies at the front of the hand in
 * shuffled-pool order (see its KDoc) — combined with that ordering, a plain scan is sufficient and
 * needs no extra bookkeeping on [Card] itself.
 *
 * @param hand The current hand to scan.
 * @param forced scryfallId → forced copy count (the "Custom your hand" selection).
 * @return The set of [hand] indices that must not be selectable for bottoming.
 */
fun computeProtectedIndices(hand: List<Card>, forced: Map<String, Int>): Set<Int> {
    if (forced.isEmpty()) return emptySet()
    val taken = mutableMapOf<String, Int>()
    val protected = mutableSetOf<Int>()
    hand.forEachIndexed { index, card ->
        val need = forced[card.scryfallId] ?: 0
        val takenSoFar = taken[card.scryfallId] ?: 0
        if (takenSoFar < need) {
            protected += index
            taken[card.scryfallId] = takenSoFar + 1
        }
    }
    return protected
}
