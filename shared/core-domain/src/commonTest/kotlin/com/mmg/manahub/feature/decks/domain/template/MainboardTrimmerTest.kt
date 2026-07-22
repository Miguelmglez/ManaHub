package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.card
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Wizard Quality Campaign Wave 2 -- direct, pure unit coverage for [MainboardTrimmer.trim],
 * independent of [BuildDeckFromTemplateUseCase]'s land-target non-monotonicity (which is what
 * triggers an overshoot in production, but is not itself under test here — see
 * [MainboardTrimmer]'s KDoc for why the function is extracted standalone).
 */
class MainboardTrimmerTest {

    private fun entry(id: String, name: String, quantity: Int = 1) =
        DeckEntry(card = card(id = id, name = name), quantity = quantity, isOwned = true, isSideboard = false)

    /** Fixed fit scores by scryfallId -- lower score = worse fit = trimmed first. */
    private fun scoreOf(scores: Map<String, Float>): (com.mmg.manahub.core.model.Card) -> Float =
        { card -> scores[card.scryfallId] ?: 0f }

    @Test
    fun `no trim when not oversize`() {
        val entries = listOf(entry("a", "A"), entry("b", "B"))
        val result = MainboardTrimmer.trim(entries, emptySet(), emptySet(), overshootCount = 0, scoreOf = scoreOf(emptyMap()))
        assertEquals(entries, result)
    }

    @Test
    fun `oversize by 1 removes exactly the single worst-fit card`() {
        val entries = listOf(
            entry("best", "Best"),
            entry("mid", "Mid"),
            entry("worst", "Worst"),
        )
        val scores = mapOf("best" to 0.9f, "mid" to 0.5f, "worst" to 0.1f)
        val result = MainboardTrimmer.trim(entries, emptySet(), emptySet(), overshootCount = 1, scoreOf = scoreOf(scores))
        assertEquals(2, result.sumOf { it.quantity })
        assertTrue(result.none { it.card.scryfallId == "worst" }, "the lowest-fit card must be the one trimmed")
        assertTrue(result.any { it.card.scryfallId == "best" })
        assertTrue(result.any { it.card.scryfallId == "mid" })
    }

    @Test
    fun `oversize by 2 removes the two worst-fit cards in ascending score order`() {
        val entries = listOf(
            entry("a", "A"),
            entry("b", "B"),
            entry("c", "C"),
            entry("d", "D"),
        )
        val scores = mapOf("a" to 0.9f, "b" to 0.7f, "c" to 0.3f, "d" to 0.1f)
        val result = MainboardTrimmer.trim(entries, emptySet(), emptySet(), overshootCount = 2, scoreOf = scoreOf(scores))
        assertEquals(2, result.size)
        val remainingIds = result.map { it.card.scryfallId }.toSet()
        assertEquals(setOf("a", "b"), remainingIds, "must keep the two best-fit cards, trim the two worst")
    }

    @Test
    fun `a multi-copy entry is decremented before being removed outright`() {
        val entries = listOf(entry("best", "Best"), entry("stack", "Stack", quantity = 3))
        val scores = mapOf("best" to 0.9f, "stack" to 0.1f)
        val result = MainboardTrimmer.trim(entries, emptySet(), emptySet(), overshootCount = 2, scoreOf = scoreOf(scores))
        val stackEntry = result.first { it.card.scryfallId == "stack" }
        assertEquals(1, stackEntry.quantity, "removing 2 of 3 copies must decrement, not drop, the entry")
        assertEquals(2, result.sumOf { it.quantity }, "original total 4 minus the 2 trimmed copies")
    }

    @Test
    fun `seed and commander protected cards are never trimmed even when they are the worst fit`() {
        val entries = listOf(
            entry("seed", "Seed Card"),
            entry("commander", "The Commander"),
            entry("filler", "Filler"),
        )
        // The protected cards score WORSE than the filler -- without protection they'd be trimmed
        // first; the trim must skip them regardless of fit.
        val scores = mapOf("seed" to 0.0f, "commander" to 0.0f, "filler" to 0.5f)
        val result = MainboardTrimmer.trim(
            entries = entries,
            protectedNames = setOf("Seed Card"),
            protectedIds = setOf("commander"),
            overshootCount = 3, // even asking to trim everything must spare the protected set
            scoreOf = scoreOf(scores),
        )
        assertEquals(setOf("seed", "commander"), result.map { it.card.scryfallId }.toSet())
    }

    @Test
    fun `deterministic tie-break by name then scryfallId when scores are equal`() {
        val entries = listOf(
            entry("z1", "Zeta"),
            entry("a1", "Alpha"),
            entry("a2", "Alpha"),
        )
        // All tied at the same score -- ascending name, then scryfallId, decides trim order.
        val result = MainboardTrimmer.trim(entries, emptySet(), emptySet(), overshootCount = 1, scoreOf = scoreOf(emptyMap()))
        assertTrue(result.none { it.card.scryfallId == "a1" }, "Alpha/a1 sorts first alphabetically and must be trimmed first")
    }

    @Test
    fun `trim is deterministic across repeated calls with identical inputs`() {
        val entries = listOf(entry("a", "A"), entry("b", "B"), entry("c", "C"))
        val scores = mapOf("a" to 0.8f, "b" to 0.4f, "c" to 0.2f)
        val first = MainboardTrimmer.trim(entries, emptySet(), emptySet(), overshootCount = 1, scoreOf = scoreOf(scores))
        val second = MainboardTrimmer.trim(entries, emptySet(), emptySet(), overshootCount = 1, scoreOf = scoreOf(scores))
        assertEquals(first.map { it.card.scryfallId to it.quantity }, second.map { it.card.scryfallId to it.quantity })
    }
}
