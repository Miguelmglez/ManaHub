package com.mmg.manahub.feature.playtest.domain.usecase

import com.mmg.manahub.core.model.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [drawWithForced] — the shared forced-aware draw primitive behind the initial
 * draw, redraw, London mulligan, and "Custom your hand" instant re-apply.
 */
class ForcedHandDrawerTest {

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

    // ── No forced cards: must behave exactly like a plain draw ─────────────────

    @Test
    fun `given no forced cards then hand equals count and library holds the remainder`() {
        val pool = (1..20).map { makeCard("card-$it") }

        val (hand, library) = drawWithForced(pool, count = 7, forced = emptyMap())

        assertEquals(7, hand.size)
        assertEquals(13, library.size)
    }

    @Test
    fun `given count larger than pool then hand is clamped to pool size`() {
        val pool = listOf(makeCard("a"), makeCard("b"), makeCard("c"))

        val (hand, library) = drawWithForced(pool, count = 7, forced = emptyMap())

        assertEquals(3, hand.size)
        assertTrue(library.isEmpty())
    }

    // ── Forced cards guaranteed in hand ─────────────────────────────────────────

    @Test
    fun `given a forced card when drawn then it is present in the hand`() {
        val pool = listOf(makeCard("bolt")) + (1..19).map { makeCard("filler-$it") }

        val (hand, _) = drawWithForced(pool, count = 7, forced = mapOf("bolt" to 1))

        assertTrue("forced card must be present", hand.any { it.scryfallId == "bolt" })
        assertEquals(7, hand.size)
    }

    @Test
    fun `given multiple copies of a forced card in the pool then all forced copies land in hand`() {
        val pool = List(3) { makeCard("bolt") } + (1..17).map { makeCard("filler-$it") }

        val (hand, _) = drawWithForced(pool, count = 7, forced = mapOf("bolt" to 3))

        assertEquals(3, hand.count { it.scryfallId == "bolt" })
        assertEquals(7, hand.size)
    }

    @Test
    fun `given a forced count exceeding pool availability then only the available copies are forced`() {
        // Only 2 copies of "bolt" exist in the pool, but 5 are requested — must never invent copies.
        val pool = List(2) { makeCard("bolt") } + (1..18).map { makeCard("filler-$it") }

        val (hand, library) = drawWithForced(pool, count = 7, forced = mapOf("bolt" to 5))

        assertEquals(2, hand.count { it.scryfallId == "bolt" })
        assertEquals(7, hand.size)
        assertEquals(13, library.size)
        // No phantom "bolt" copies anywhere.
        assertEquals(2, (hand + library).count { it.scryfallId == "bolt" })
    }

    @Test
    fun `given forced cards fewer than count then the rest is filled from the shuffled remainder`() {
        val pool = listOf(makeCard("bolt")) + (1..19).map { makeCard("filler-$it") }

        val (hand, library) = drawWithForced(pool, count = 7, forced = mapOf("bolt" to 1))

        assertEquals(7, hand.size)
        assertEquals(13, library.size)
        // The 6 non-forced hand cards must all be filler (not duplicated, not invented).
        val nonForced = hand.filterNot { it.scryfallId == "bolt" }
        assertEquals(6, nonForced.size)
        assertTrue(nonForced.all { it.scryfallId.startsWith("filler-") })
    }

    @Test
    fun `given total forced count equal to count then hand is exactly the forced cards`() {
        val pool = List(7) { makeCard("bolt") } + (1..13).map { makeCard("filler-$it") }

        val (hand, library) = drawWithForced(pool, count = 7, forced = mapOf("bolt" to 7))

        assertEquals(7, hand.count { it.scryfallId == "bolt" })
        assertEquals(7, hand.size)
        assertEquals(13, library.size)
    }

    @Test
    fun `given forced cards then total hand plus library size is conserved`() {
        val pool = List(3) { makeCard("bolt") } + (1..27).map { makeCard("filler-$it") }

        val (hand, library) = drawWithForced(pool, count = 10, forced = mapOf("bolt" to 3))

        assertEquals(30, hand.size + library.size)
    }

    @Test
    fun `given multiple distinct forced cards then all of them appear in the hand`() {
        val pool = listOf(makeCard("bolt"), makeCard("birds")) + (1..18).map { makeCard("filler-$it") }

        val (hand, _) = drawWithForced(pool, count = 7, forced = mapOf("bolt" to 1, "birds" to 1))

        assertTrue(hand.any { it.scryfallId == "bolt" })
        assertTrue(hand.any { it.scryfallId == "birds" })
    }
}
