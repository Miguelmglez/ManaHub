package com.mmg.manahub.feature.decks.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/** Deck Wizard v4, W0.3 (G8a/E1): [CardSection.realCount] is the literal card count, independent
 * of the confidence-weighted [CardSection.current] the score consumes. */
class CardSectionRealCountTest {

    @Test
    fun `realCount sums contribution quantities, ignoring the weighted current entirely`() {
        val section = CardSection(
            id = "role:removal_spot",
            label = "Removal",
            current = 8, // the weighted, rounded value a partially-confident card can inflate/deflate
            min = 6,
            ideal = 10,
            max = 14,
            contributions = listOf(
                CardContribution(scryfallId = "a", quantity = 3, confidence = 1f),
                CardContribution(scryfallId = "b", quantity = 2, confidence = 0.4f),
                CardContribution(scryfallId = "c", quantity = 4, confidence = 1f),
            ),
        )
        assertEquals(9, section.realCount, "realCount must be the literal Σquantity, never the rounded/weighted current")
    }

    @Test
    fun `realCount is zero for an empty section, independent of current`() {
        val section = CardSection(id = "offplan", label = "Off-plan", current = 0, contributions = emptyList())
        assertEquals(0, section.realCount)
    }
}
