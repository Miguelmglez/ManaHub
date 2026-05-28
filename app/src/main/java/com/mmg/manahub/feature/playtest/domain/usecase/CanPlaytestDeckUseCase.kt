package com.mmg.manahub.feature.playtest.domain.usecase

import com.mmg.manahub.core.domain.model.DeckWithCards
import com.mmg.manahub.feature.playtest.domain.model.PlaytestEligibility
import javax.inject.Inject

/**
 * Determines whether a deck is eligible for playtesting.
 *
 * Eligibility rules (sideboard excluded from all counts):
 *   - standard : mainboard card count >= 60
 *   - draft    : mainboard card count >= 40
 *   - commander: mainboard card count == exactly 100 (includes the commander card)
 *   - any other format: ineligible
 *
 * Returns an [PlaytestEligibility] with a human-readable reason when ineligible.
 */
class CanPlaytestDeckUseCase @Inject constructor() {

    /**
     * @param deckWithCards The deck and its card slots (sideboard excluded by caller).
     * @param mainboardCount Total mainboard card count (sum of quantities, sideboard excluded).
     */
    operator fun invoke(deckWithCards: DeckWithCards, mainboardCount: Int): PlaytestEligibility {
        val format = deckWithCards.deck.format.lowercase()
        return when (format) {
            "standard" -> checkMinSize(mainboardCount, minSize = 60, format = "Standard")
            "draft"    -> checkMinSize(mainboardCount, minSize = 40, format = "Draft")
            "commander" -> checkExactSize(mainboardCount, exactSize = 100, format = "Commander")
            else -> PlaytestEligibility.Ineligible(
                reason = "Format '$format' is not supported for playtesting"
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
