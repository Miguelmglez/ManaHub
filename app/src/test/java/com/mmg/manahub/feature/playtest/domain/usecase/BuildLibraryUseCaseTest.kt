package com.mmg.manahub.feature.playtest.domain.usecase

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.model.DeckSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [BuildLibraryUseCase].
 *
 * Verifies: copy expansion, commander exclusion, missing-card skip,
 * commander-not-in-mainboard pinning behaviour, and edge inputs.
 */
class BuildLibraryUseCaseTest {

    private lateinit var useCase: BuildLibraryUseCase

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeCard(scryfallId: String) = Card(
        scryfallId        = scryfallId,
        name              = "Card $scryfallId",
        printedName       = null,
        manaCost          = "{1}",
        cmc               = 1.0,
        colors            = emptyList(),
        colorIdentity     = emptyList(),
        typeLine          = "Creature",
        printedTypeLine   = null,
        oracleText        = null,
        printedText       = null,
        keywords          = emptyList(),
        power             = "1",
        toughness         = "1",
        loyalty           = null,
        setCode           = "M21",
        setName           = "Core Set 2021",
        collectorNumber   = "001",
        rarity            = "common",
        releasedAt        = "2021-07-23",
        frameEffects      = emptyList(),
        promoTypes        = emptyList(),
        lang              = "en",
        imageNormal       = null,
        imageArtCrop      = null,
        imageBackNormal   = null,
        priceUsd          = null,
        priceUsdFoil      = null,
        priceEur          = null,
        priceEurFoil      = null,
        legalityStandard  = "legal",
        legalityPioneer   = "legal",
        legalityModern    = "legal",
        legalityCommander = "legal",
        flavorText        = null,
        artist            = "Test Artist",
        scryfallUri       = "https://scryfall.com/card/m21/001",
    )

    @Before
    fun setUp() {
        useCase = BuildLibraryUseCase()
    }

    // ── Group 1: copy count expansion ─────────────────────────────────────────

    @Test
    fun `given slot with quantity 4 when invoked then library contains 4 copies`() {
        // Arrange
        val card = makeCard("bolt-id")
        val slots = listOf(DeckSlot(scryfallId = "bolt-id", quantity = 4))
        val lookup = mapOf("bolt-id" to card)

        // Act
        val library = useCase(mainboardSlots = slots, cardLookup = lookup)

        // Assert — exactly 4 copies
        assertEquals(4, library.size)
        assertTrue(library.all { it.scryfallId == "bolt-id" })
    }

    @Test
    fun `given multiple slots when invoked then total equals sum of quantities`() {
        // Arrange
        val slots = listOf(
            DeckSlot("a", 4),
            DeckSlot("b", 3),
            DeckSlot("c", 2),
        )
        val lookup = mapOf(
            "a" to makeCard("a"),
            "b" to makeCard("b"),
            "c" to makeCard("c"),
        )

        // Act
        val library = useCase(slots, lookup)

        // Assert
        assertEquals(9, library.size)
        assertEquals(4, library.count { it.scryfallId == "a" })
        assertEquals(3, library.count { it.scryfallId == "b" })
        assertEquals(2, library.count { it.scryfallId == "c" })
    }

    @Test
    fun `given slot with quantity 1 when invoked then library contains exactly 1 copy`() {
        val slots = listOf(DeckSlot("x", 1))
        val lookup = mapOf("x" to makeCard("x"))

        val library = useCase(slots, lookup)

        assertEquals(1, library.size)
    }

    // ── Group 2: commander exclusion ──────────────────────────────────────────

    @Test
    fun `given commander id when invoked then commander is excluded from library`() {
        // Arrange — 99 non-commander slots (simulate a typical commander deck minus commander)
        val commanderId = "commander-id"
        val slots = mutableListOf<DeckSlot>()
        // 1 commander slot (quantity 1)
        slots.add(DeckSlot(commanderId, 1))
        // 99 other unique cards (quantity 1 each)
        (1..99).forEach { slots.add(DeckSlot("card-$it", 1)) }
        val lookup = mutableMapOf<String, Card>()
        slots.forEach { lookup[it.scryfallId] = makeCard(it.scryfallId) }

        // Act
        val library = useCase(
            mainboardSlots      = slots,
            cardLookup          = lookup,
            commanderScryfallId = commanderId,
        )

        // Assert — exactly 99 cards, commander absent
        assertEquals(99, library.size)
        assertFalse(library.any { it.scryfallId == commanderId })
    }

    @Test
    fun `given null commander id when invoked then no card is excluded`() {
        val slots = listOf(DeckSlot("a", 4), DeckSlot("b", 4))
        val lookup = mapOf("a" to makeCard("a"), "b" to makeCard("b"))

        val library = useCase(slots, lookup, commanderScryfallId = null)

        assertEquals(8, library.size)
    }

    @Test
    fun `given commander not in mainboard when invoked then library size is full mainboard size`() {
        // Contract pin: if the commander is set but NOT in mainboard slots,
        // BuildLibraryUseCase does NOT reduce library size — it simply has nothing
        // to exclude. PlaytestSetupViewModel guards against this misconfiguration
        // BEFORE invoking BuildLibraryUseCase.
        val commanderId = "ghost-commander-id"
        val slots = listOf(DeckSlot("a", 4), DeckSlot("b", 3))
        val lookup = mapOf("a" to makeCard("a"), "b" to makeCard("b"))

        val library = useCase(slots, lookup, commanderScryfallId = commanderId)

        // Library is the full 7 cards — no reduction because the slot was never in the list.
        assertEquals(7, library.size)
    }

    // ── Group 3: missing card lookup (skip behaviour) ─────────────────────────

    @Test
    fun `given card missing from lookup when invoked then that slot is skipped`() {
        val slots = listOf(DeckSlot("present", 2), DeckSlot("missing", 3))
        val lookup = mapOf("present" to makeCard("present"))
        // "missing" is not in lookup

        val library = useCase(slots, lookup)

        // Only the 2 "present" copies are included.
        assertEquals(2, library.size)
        assertTrue(library.all { it.scryfallId == "present" })
    }

    @Test
    fun `given empty lookup when invoked then library is empty`() {
        val slots = listOf(DeckSlot("a", 4))
        val library = useCase(slots, emptyMap())

        assertEquals(0, library.size)
    }

    // ── Group 4: edge inputs ──────────────────────────────────────────────────

    @Test
    fun `given empty mainboard slots when invoked then library is empty`() {
        val library = useCase(emptyList(), emptyMap())

        assertEquals(0, library.size)
    }

    @Test
    fun `given standard 60-card deck when invoked then library size is 60`() {
        // Simulate a typical 4-of standard deck: 15 distinct cards × 4 copies.
        val slots = (1..15).map { DeckSlot("card-$it", 4) }
        val lookup = slots.associate { it.scryfallId to makeCard(it.scryfallId) }

        val library = useCase(slots, lookup)

        assertEquals(60, library.size)
    }

    @Test
    fun `given identical slot repeated when invoked then copies are accumulated`() {
        // Two slots with the same scryfallId but different quantities (edge case in deck data).
        val slots = listOf(DeckSlot("a", 2), DeckSlot("a", 1))
        val lookup = mapOf("a" to makeCard("a"))

        val library = useCase(slots, lookup)

        assertEquals(3, library.size)
    }
}
