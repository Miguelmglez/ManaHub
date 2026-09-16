package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.DeckFormat
import kotlin.test.Test
import kotlin.test.assertEquals

/** Deck Wizard 60-card wave (v6), plan §5 Phase 1.1 gate. */
class CopyPolicyTest {

    @Test
    fun `a basic land is always placeable at Int MAX_VALUE regardless of owned quantity`() {
        val card = card(typeLine = "Basic Land — Forest")
        assertEquals(Int.MAX_VALUE, CopyPolicy.maxPlaceable(card, DeckFormat.STANDARD, owned = 1))
        assertEquals(Int.MAX_VALUE, CopyPolicy.maxPlaceable(card, DeckFormat.STANDARD, owned = null))
        assertEquals(Int.MAX_VALUE, CopyPolicy.maxPlaceable(card, DeckFormat.STANDARD, owned = 0))
    }

    @Test
    fun `a Commander-shaped format caps every card at 1 copy regardless of owned quantity`() {
        val candidate = card()
        listOf(DeckFormat.COMMANDER, DeckFormat.COMMANDER_CASUAL).forEach { format ->
            assertEquals(1, CopyPolicy.maxPlaceable(candidate, format, owned = 7))
            assertEquals(1, CopyPolicy.maxPlaceable(candidate, format, owned = null))
        }
    }

    @Test
    fun `a Vintage-restricted card caps at 1 even when owned exceeds 1`() {
        val restricted = card(legalityVintage = "restricted")
        assertEquals(1, CopyPolicy.maxPlaceable(restricted, DeckFormat.VINTAGE, owned = 7))
        val legal = card(legalityVintage = "legal")
        assertEquals(4, CopyPolicy.maxPlaceable(legal, DeckFormat.VINTAGE, owned = 7))
    }

    @Test
    fun `restricted-in-Vintage does not restrict the same card in a different format`() {
        val restricted = card(legalityVintage = "restricted")
        assertEquals(4, CopyPolicy.maxPlaceable(restricted, DeckFormat.MODERN, owned = 7))
    }

    @Test
    fun `owned quantity below format maxCopies clamps to owned`() {
        assertEquals(2, CopyPolicy.maxPlaceable(card(), DeckFormat.STANDARD, owned = 2))
    }

    @Test
    fun `owned quantity above format maxCopies clamps to maxCopies (4)`() {
        assertEquals(4, CopyPolicy.maxPlaceable(card(), DeckFormat.STANDARD, owned = 7))
    }

    @Test
    fun `owned null is ownership-exempt and clamps to format maxCopies only`() {
        assertEquals(4, CopyPolicy.maxPlaceable(card(), DeckFormat.STANDARD, owned = null))
    }

    @Test
    fun `a non-basic land is NOT ownership-exempt -- only isBasicLand qualifies`() {
        val dual = card(typeLine = "Land")
        assertEquals(4, CopyPolicy.maxPlaceable(dual, DeckFormat.STANDARD, owned = 7))
    }

    @Test
    fun `maxSeedCopies ignores ownership and equals maxPlaceable with owned = null`() {
        val cases = listOf(
            DeckFormat.STANDARD to card(),
            DeckFormat.COMMANDER to card(),
            DeckFormat.VINTAGE to card(legalityVintage = "restricted"),
            DeckFormat.STANDARD to card(typeLine = "Basic Land — Forest"),
        )
        cases.forEach { (format, candidate) ->
            assertEquals(
                CopyPolicy.maxPlaceable(candidate, format, owned = null),
                CopyPolicy.maxSeedCopies(candidate, format),
            )
        }
    }
}
