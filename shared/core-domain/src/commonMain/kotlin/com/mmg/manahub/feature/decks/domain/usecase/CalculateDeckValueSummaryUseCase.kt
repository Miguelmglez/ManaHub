package com.mmg.manahub.feature.decks.domain.usecase
// COMMENTS_REVIEWED: 2026-09-16

import com.mmg.manahub.core.model.DeckSlotEntry
import com.mmg.manahub.core.model.PreferredCurrency

/** The calculated value and price coverage for one deck board. */
data class BoardValue(
    val knownTotal: Double,
    val knownCopies: Int,
    val missingPriceCopies: Int,
    val isEmpty: Boolean,
)

/** The calculated value and price coverage for both deck boards. */
data class DeckValueSummary(
    val mainboard: BoardValue,
    val sideboard: BoardValue,
)

/** Calculates normal-printing deck value without substituting one currency for another. */
class CalculateDeckValueSummaryUseCase {

    /** Calculates separate mainboard and sideboard summaries from all [entries]. */
    operator fun invoke(
        entries: List<DeckSlotEntry>,
        preferredCurrency: PreferredCurrency,
    ): DeckValueSummary = DeckValueSummary(
        mainboard = calculateBoard(entries.filterNot { it.isSideboard }, preferredCurrency),
        sideboard = calculateBoard(entries.filter { it.isSideboard }, preferredCurrency),
    )

    private fun calculateBoard(
        entries: List<DeckSlotEntry>,
        preferredCurrency: PreferredCurrency,
    ): BoardValue {
        var knownTotal = 0.0
        var knownCopies = 0
        var missingPriceCopies = 0

        entries.forEach { entry ->
            val price = entry.card?.let { card ->
                when (preferredCurrency) {
                    PreferredCurrency.EUR -> card.priceEur
                    PreferredCurrency.USD -> card.priceUsd
                }
            }
            val validPrice = price?.takeIf { it.isFinite() && it >= 0.0 }
            if (validPrice == null) {
                missingPriceCopies += entry.quantity
            } else {
                knownTotal += validPrice * entry.quantity
                knownCopies += entry.quantity
            }
        }

        return BoardValue(
            knownTotal = knownTotal,
            knownCopies = knownCopies,
            missingPriceCopies = missingPriceCopies,
            isEmpty = entries.isEmpty(),
        )
    }
}
