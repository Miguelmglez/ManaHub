package com.mmg.manahub.feature.playtest.domain.usecase

import com.mmg.manahub.core.domain.model.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DrawHandUseCase].
 *
 * Verifies: correct split, library shrinkage, boundary conditions.
 */
class DrawHandUseCaseTest {

    private lateinit var useCase: DrawHandUseCase

    private fun makeCard(id: String) = Card(
        scryfallId = id, name = "Card $id", printedName = null, manaCost = null, cmc = 0.0,
        colors = emptyList(), colorIdentity = emptyList(), typeLine = "Instant",
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

    private fun makeLibrary(size: Int) = (1..size).map { makeCard("card-$it") }

    @Before
    fun setUp() {
        useCase = DrawHandUseCase()
    }

    // ── Group 1: normal draws ─────────────────────────────────────────────────

    @Test
    fun `given library of 53 when drawing 7 then hand has 7 cards`() {
        val library = makeLibrary(53)

        val (hand, _) = useCase(library, count = 7)

        assertEquals(7, hand.size)
    }

    @Test
    fun `given library of 53 when drawing 7 then remaining library has 46 cards`() {
        val library = makeLibrary(53)

        val (_, remaining) = useCase(library, count = 7)

        assertEquals(46, remaining.size)
    }

    @Test
    fun `given library when drawing then hand contains top N cards`() {
        // Library is ordered; use case must take from the front.
        val library = (1..10).map { makeCard("card-$it") }

        val (hand, remaining) = useCase(library, count = 3)

        // Hand should be cards 1, 2, 3 (indices 0-2 of the library).
        assertEquals(listOf("card-1", "card-2", "card-3"), hand.map { it.scryfallId })
        // Remaining should start at card-4.
        assertEquals("card-4", remaining.first().scryfallId)
    }

    @Test
    fun `given library when drawing then hand size plus remaining size equals library size`() {
        val library = makeLibrary(60)

        val (hand, remaining) = useCase(library, count = 7)

        assertEquals(60, hand.size + remaining.size)
    }

    // ── Group 2: boundary — drawing exactly the library size ──────────────────

    @Test
    fun `given library of 7 when drawing exactly 7 then hand is full library`() {
        val library = makeLibrary(7)

        val (hand, remaining) = useCase(library, count = 7)

        assertEquals(7, hand.size)
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun `given library of 1 when drawing 1 then hand has 1 card and library is empty`() {
        val library = makeLibrary(1)

        val (hand, remaining) = useCase(library, count = 1)

        assertEquals(1, hand.size)
        assertTrue(remaining.isEmpty())
    }

    // ── Group 3: boundary — drawing more than the library ────────────────────

    @Test
    fun `given library of 3 when drawing 7 then hand is clamped to library size`() {
        // N > library.size: use case must coerce to avoid IndexOutOfBoundsException.
        val library = makeLibrary(3)

        val (hand, remaining) = useCase(library, count = 7)

        assertEquals(3, hand.size)
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun `given empty library when drawing 7 then hand is empty`() {
        val (hand, remaining) = useCase(emptyList(), count = 7)

        assertTrue(hand.isEmpty())
        assertTrue(remaining.isEmpty())
    }

    // ── Group 4: edge — drawing 0 cards ──────────────────────────────────────

    @Test
    fun `given library when drawing 0 then hand is empty and library is unchanged`() {
        val library = makeLibrary(10)

        val (hand, remaining) = useCase(library, count = 0)

        assertTrue(hand.isEmpty())
        assertEquals(10, remaining.size)
    }
}
