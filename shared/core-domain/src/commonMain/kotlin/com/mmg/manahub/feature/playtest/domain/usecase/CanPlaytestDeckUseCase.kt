package com.mmg.manahub.feature.playtest.domain.usecase

import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.DeckWithCards
import com.mmg.manahub.core.model.PlaytestEligibility

/**
 * Determines whether a deck is eligible for playtesting.
 *
 * Eligibility rules (sideboard excluded from all counts) are derived from [DeckFormat]'s own
 * shape properties rather than a hardcoded format-name list — this is deliberate: a previous
 * version of this use case matched a hardcoded `when` over 4 format strings
 * ("standard"/"casual"/"draft"/"commander"), which silently fell through to
 * [PlaytestEligibility.Ineligible] for every 60-card format added later (Pioneer/Modern/Legacy/
 * Vintage/Pauper, Deck Doctor Phase 4). Deriving from [DeckFormat.isSixtyCardConstructed] /
 * [DeckFormat.requiresCommander] / [DeckFormat.targetDeckSize] means a future format that fits
 * one of those existing shapes is automatically covered without touching this file again:
 *   - [DeckFormat.requiresCommander] (Commander): mainboard card count == [DeckFormat.targetDeckSize] exactly
 *   - [DeckFormat.isSixtyCardConstructed] (Standard, Pioneer, Modern, Legacy, Vintage, Pauper,
 *     Casual) or Draft: mainboard card count >= [DeckFormat.targetDeckSize]
 *   - a format string that doesn't match any [DeckFormat] entry, or matches an entry with a
 *     shape not covered above: ineligible
 *
 * Returns an [PlaytestEligibility] with a human-readable reason when ineligible.
 */
class CanPlaytestDeckUseCase {

    /**
     * @param deckWithCards The deck and its card slots (sideboard excluded by caller).
     * @param mainboardCount Total mainboard card count (sum of quantities, sideboard excluded).
     */
    operator fun invoke(deckWithCards: DeckWithCards, mainboardCount: Int): PlaytestEligibility {
        val formatString = deckWithCards.deck.format
        val format = DeckFormat.entries.firstOrNull { it.name.equals(formatString, ignoreCase = true) }
            ?: return PlaytestEligibility.Ineligible(
                reason = "Format '$formatString' is not supported for playtesting"
            )

        return when {
            format.requiresCommander ->
                checkExactSize(mainboardCount, exactSize = format.targetDeckSize, format = format.displayName)
            format.isSixtyCardConstructed || format == DeckFormat.DRAFT ->
                checkMinSize(mainboardCount, minSize = format.targetDeckSize, format = format.displayName)
            else -> PlaytestEligibility.Ineligible(
                reason = "Format '${format.displayName}' is not supported for playtesting"
            )
        }
    }

    private fun checkMinSize(count: Int, minSize: Int, format: String): PlaytestEligibility =
        if (count >= minSize) {
            PlaytestEligibility.Eligible
        } else {
            PlaytestEligibility.Ineligible(
                reason = "$format needs $minSize cards — you have $count"
            )
        }

    private fun checkExactSize(count: Int, exactSize: Int, format: String): PlaytestEligibility =
        if (count == exactSize) {
            PlaytestEligibility.Eligible
        } else {
            val diff = exactSize - count
            PlaytestEligibility.Ineligible(
                reason = if (diff > 0) {
                    "$format decks must have exactly $exactSize cards — you need $diff more"
                } else {
                    "$format decks must have exactly $exactSize cards — you have ${count - exactSize} too many"
                }
            )
        }
}
