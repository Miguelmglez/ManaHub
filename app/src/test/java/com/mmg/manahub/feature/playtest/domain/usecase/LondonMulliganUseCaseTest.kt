package com.mmg.manahub.feature.playtest.domain.usecase

import com.mmg.manahub.core.model.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [LondonMulliganUseCase].
 *
 * Verifies: card conservation across reshuffle, drawCount always honoured,
 * applyBottomN card removal and library append.
 */
class LondonMulliganUseCaseTest {

    private lateinit var useCase: LondonMulliganUseCase

    private fun makeCard(id: String) = Card(
        scryfallId = id, name = "Card $id", printedName = null, manaCost = null, cmc = 0.0,
        colors = emptyList(), colorIdentity = emptyList(), typeLine = "Sorcery",
        printedTypeLine = null, oracleText = null, printedText = null, keywords = emptyList(),
        power = null, toughness = null, loyalty = null, setCode = "TST", setName = "Test",
        collectorNumber = "1", rarity = "common", releasedAt = "2024-01-01",
        frameEffects = emptyList(), promoTypes = emptyList(), lang = "en",
        imageNormal = null, imageArtCrop = null, imageBackNormal = null,
        priceUsd = null, priceUsdFoil = null, priceEur = null, priceEurFoil = null,
        legalityStandard = "legal", legalityPioneer = "legal", legalityModern = "legal",
        legalityCommander = "legal", flavorText = null, artist = null,
        scryfallUri = "https://scryfall.com",
    )

    @Before
    fun setUp() {
        useCase = LondonMulliganUseCase()
    }

    // ── Group 1: card conservation ────────────────────────────────────────────

    @Test
    fun `given hand and library when mulligan then combined card count is conserved`() {
        // 5 in hand + 48 remaining = 53 total; must still be 53 after reshuffle.
        val hand    = (1..5).map  { makeCard("h$it") }
        val library = (1..48).map { makeCard("l$it") }

        val (newHand, newLibrary) = useCase(hand, library, drawCount = 7)

        assertEquals(53, newHand.size + newLibrary.size)
    }

    @Test
    fun `given hand and library when mulligan then hand always equals drawCount`() {
        // drawCount = 7, total pool = 53 (> 7) so hand should always be 7.
        val hand    = (1..7).map  { makeCard("h$it") }
        val library = (1..46).map { makeCard("l$it") }

        val (newHand, _) = useCase(hand, library, drawCount = 7)

        assertEquals(7, newHand.size)
    }

    @Test
    fun `given hand and library when mulligan then all original cards are still present in combined result`() {
        val hand    = listOf(makeCard("bolt"), makeCard("birds"), makeCard("explore"))
        val library = listOf(makeCard("land"), makeCard("ritual"))

        val (newHand, newLibrary) = useCase(hand, library, drawCount = 3)

        val combined = newHand + newLibrary
        val scryfallIds = combined.map { it.scryfallId }.toSet()
        assertTrue(scryfallIds.containsAll(listOf("bolt", "birds", "explore", "land", "ritual")))
    }

    @Test
    fun `given drawCount larger than pool when mulligan then hand is clamped to pool size`() {
        // Pool of 3 cards, drawCount = 7 — use case must coerce.
        val hand    = listOf(makeCard("a"))
        val library = listOf(makeCard("b"), makeCard("c"))

        val (newHand, newLibrary) = useCase(hand, library, drawCount = 7)

        assertEquals(3, newHand.size)
        assertTrue(newLibrary.isEmpty())
    }

    // ── Group 2: applyBottomN ─────────────────────────────────────────────────

    @Test
    fun `given hand of 7 when bottoming 1 card then final hand has 6 cards`() {
        val hand = (1..7).map { makeCard("card-$it") }
        val library = emptyList<Card>()

        val (finalHand, _) = useCase.applyBottomN(hand, library, cardsToBottom = listOf(0))

        assertEquals(6, finalHand.size)
    }

    @Test
    fun `given hand when bottoming card at index 0 then that card is removed from hand`() {
        val hand = (1..7).map { makeCard("card-$it") }
        val library = emptyList<Card>()

        val (finalHand, _) = useCase.applyBottomN(hand, library, cardsToBottom = listOf(0))

        assertFalse(finalHand.any { it.scryfallId == "card-1" })
    }

    @Test
    fun `given hand when bottoming 1 card then bottomed card is appended to library`() {
        val hand = (1..7).map { makeCard("card-$it") }
        val library = listOf(makeCard("lib-1"))

        val (_, finalLibrary) = useCase.applyBottomN(hand, library, cardsToBottom = listOf(0))

        // card-1 was bottomed; original lib-1 was already there.
        assertEquals(2, finalLibrary.size)
        assertEquals("card-1", finalLibrary.last().scryfallId)
    }

    @Test
    fun `given hand when bottoming 2 cards then final hand has hand-size minus 2`() {
        val hand = (1..7).map { makeCard("card-$it") }
        val library = emptyList<Card>()

        val (finalHand, _) = useCase.applyBottomN(hand, library, cardsToBottom = listOf(0, 1))

        assertEquals(5, finalHand.size)
    }

    @Test
    fun `given hand when bottoming multiple cards then total card count is conserved`() {
        val hand    = (1..7).map  { makeCard("card-$it") }
        val library = listOf(makeCard("lib-card"))

        val (finalHand, finalLibrary) = useCase.applyBottomN(
            hand, library, cardsToBottom = listOf(0, 2)
        )

        // 7 hand + 1 library = 8 total before; must equal after.
        assertEquals(8, finalHand.size + finalLibrary.size)
    }

    @Test
    fun `given hand when bottoming 0 cards then hand and library are unchanged`() {
        val hand    = (1..7).map { makeCard("card-$it") }
        val library = (1..3).map { makeCard("lib-$it") }

        val (finalHand, finalLibrary) = useCase.applyBottomN(hand, library, cardsToBottom = emptyList())

        assertEquals(7, finalHand.size)
        assertEquals(3, finalLibrary.size)
    }

    @Test
    fun `given hand when bottoming specific index then remaining hand preserves order of other cards`() {
        // Cards: [A, B, C, D] — bottom index 1 (B); remaining should be [A, C, D].
        val hand = listOf(
            makeCard("A"), makeCard("B"), makeCard("C"), makeCard("D"),
        )
        val library = emptyList<Card>()

        val (finalHand, _) = useCase.applyBottomN(hand, library, cardsToBottom = listOf(1))

        assertEquals(listOf("A", "C", "D"), finalHand.map { it.scryfallId })
    }

    @Test
    fun `given hand when bottoming last index then it is appended to library bottom`() {
        val hand = listOf(makeCard("A"), makeCard("B"), makeCard("C"))
        val library = listOf(makeCard("LIB"))

        val (_, finalLibrary) = useCase.applyBottomN(hand, library, cardsToBottom = listOf(2))

        // Library: [LIB, C] — C is appended at the bottom.
        assertEquals("C", finalLibrary.last().scryfallId)
    }
}
