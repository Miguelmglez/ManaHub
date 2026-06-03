package com.mmg.manahub.feature.playtest.domain.usecase

import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.model.DeckSlot
import com.mmg.manahub.core.domain.model.DeckWithCards
import com.mmg.manahub.feature.playtest.domain.model.PlaytestEligibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CanPlaytestDeckUseCase].
 *
 * Verifies format-specific thresholds (standard / draft / commander),
 * exact-size enforcement for commander, unsupported formats, and
 * case-insensitive format matching.
 */
class CanPlaytestDeckUseCaseTest {

    private lateinit var useCase: CanPlaytestDeckUseCase

    @Before
    fun setUp() {
        useCase = CanPlaytestDeckUseCase()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun deckWith(format: String) = DeckWithCards(
        deck = Deck(
            id     = "deck-1",
            name   = "Test Deck",
            format = format,
        ),
        mainboard = emptyList(),
        sideboard = emptyList(),
    )

    // ── Group 1: Standard ─────────────────────────────────────────────────────

    @Test
    fun `given standard format with exactly 60 cards then Eligible`() {
        val result = useCase(deckWith("standard"), mainboardCount = 60)
        assertEquals(PlaytestEligibility.Eligible, result)
    }

    @Test
    fun `given standard format with more than 60 cards then Eligible`() {
        val result = useCase(deckWith("standard"), mainboardCount = 80)
        assertEquals(PlaytestEligibility.Eligible, result)
    }

    @Test
    fun `given standard format with 59 cards then Ineligible`() {
        val result = useCase(deckWith("standard"), mainboardCount = 59)
        assertTrue(result is PlaytestEligibility.Ineligible)
    }

    @Test
    fun `given standard format with 59 cards then reason mentions Standard and 60`() {
        val result = useCase(deckWith("standard"), mainboardCount = 59) as PlaytestEligibility.Ineligible
        assertTrue(result.reason.contains("Standard"))
        assertTrue(result.reason.contains("60"))
    }

    @Test
    fun `given standard format with 0 cards then Ineligible`() {
        val result = useCase(deckWith("standard"), mainboardCount = 0)
        assertTrue(result is PlaytestEligibility.Ineligible)
    }

    // ── Group 2: Draft ────────────────────────────────────────────────────────

    @Test
    fun `given draft format with exactly 40 cards then Eligible`() {
        val result = useCase(deckWith("draft"), mainboardCount = 40)
        assertEquals(PlaytestEligibility.Eligible, result)
    }

    @Test
    fun `given draft format with more than 40 cards then Eligible`() {
        val result = useCase(deckWith("draft"), mainboardCount = 45)
        assertEquals(PlaytestEligibility.Eligible, result)
    }

    @Test
    fun `given draft format with 39 cards then Ineligible`() {
        val result = useCase(deckWith("draft"), mainboardCount = 39)
        assertTrue(result is PlaytestEligibility.Ineligible)
    }

    @Test
    fun `given draft format with 39 cards then reason mentions Draft and 40`() {
        val result = useCase(deckWith("draft"), mainboardCount = 39) as PlaytestEligibility.Ineligible
        assertTrue(result.reason.contains("Draft"))
        assertTrue(result.reason.contains("40"))
    }

    // ── Group 3: Commander ────────────────────────────────────────────────────

    @Test
    fun `given commander format with exactly 100 cards then Eligible`() {
        val result = useCase(deckWith("commander"), mainboardCount = 100)
        assertEquals(PlaytestEligibility.Eligible, result)
    }

    @Test
    fun `given commander format with 99 cards then Ineligible`() {
        val result = useCase(deckWith("commander"), mainboardCount = 99)
        assertTrue(result is PlaytestEligibility.Ineligible)
    }

    @Test
    fun `given commander format with 99 cards then reason says more cards needed`() {
        val result = useCase(deckWith("commander"), mainboardCount = 99) as PlaytestEligibility.Ineligible
        // The reason should indicate shortage (diff > 0 path).
        assertTrue(result.reason.contains("1"))
    }

    @Test
    fun `given commander format with 101 cards then Ineligible`() {
        // Exactly 100 is required — 101 must be rejected.
        val result = useCase(deckWith("commander"), mainboardCount = 101)
        assertTrue(result is PlaytestEligibility.Ineligible)
    }

    @Test
    fun `given commander format with 101 cards then reason says too many cards`() {
        val result = useCase(deckWith("commander"), mainboardCount = 101) as PlaytestEligibility.Ineligible
        // diff < 0 path: "you have X too many"
        assertTrue(result.reason.contains("too many"))
    }

    @Test
    fun `given commander format with 0 cards then Ineligible`() {
        val result = useCase(deckWith("commander"), mainboardCount = 0)
        assertTrue(result is PlaytestEligibility.Ineligible)
    }

    // ── Group 4: Case-insensitive format matching ──────────────────────────────

    @Test
    fun `given Commander capitalised when invoked then same rules apply as lowercase commander`() {
        val result = useCase(deckWith("Commander"), mainboardCount = 100)
        assertEquals(PlaytestEligibility.Eligible, result)
    }

    @Test
    fun `given STANDARD all-caps when invoked then same rules apply as lowercase standard`() {
        val result = useCase(deckWith("STANDARD"), mainboardCount = 60)
        assertEquals(PlaytestEligibility.Eligible, result)
    }

    @Test
    fun `given Draft mixed case when invoked then same rules apply as lowercase draft`() {
        val result = useCase(deckWith("Draft"), mainboardCount = 40)
        assertEquals(PlaytestEligibility.Eligible, result)
    }

    // ── Group 5: Unsupported formats ──────────────────────────────────────────

    @Test
    fun `given casual format then Ineligible`() {
        val result = useCase(deckWith("casual"), mainboardCount = 100)
        assertTrue(result is PlaytestEligibility.Ineligible)
    }

    @Test
    fun `given pioneer format then Ineligible`() {
        val result = useCase(deckWith("pioneer"), mainboardCount = 60)
        assertTrue(result is PlaytestEligibility.Ineligible)
    }

    @Test
    fun `given modern format then Ineligible`() {
        val result = useCase(deckWith("modern"), mainboardCount = 60)
        assertTrue(result is PlaytestEligibility.Ineligible)
    }

    @Test
    fun `given blank format then Ineligible`() {
        val result = useCase(deckWith(""), mainboardCount = 60)
        assertTrue(result is PlaytestEligibility.Ineligible)
    }

    @Test
    fun `given unsupported format reason mentions the format name`() {
        val result = useCase(deckWith("brawl"), mainboardCount = 60) as PlaytestEligibility.Ineligible
        assertTrue(result.reason.contains("brawl"))
    }
}
