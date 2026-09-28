package com.mmg.manahub.feature.decks.domain.inspirations

import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.card
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InspirationSelectionTest {

    private val bolt = card(id = "b1", name = "Lightning Bolt", typeLine = "Instant")
    private val boltPromo = card(id = "b2", name = "Lightning Bolt", typeLine = "Instant")
    private val mountain = card(id = "mtn", name = "Mountain", typeLine = "Basic Land — Mountain", colors = emptyList(), colorIdentity = emptyList())

    @Test
    fun `adding a legal card appends one copy`() {
        val result = InspirationSelection.add(emptyList(), bolt, DeckFormat.MODERN)

        assertEquals(SelectionOutcome.Changed, result.outcome)
        assertEquals(listOf(InspirationPick(bolt, 1)), result.picks)
    }

    @Test
    fun `an illegal card is rejected`() {
        val banned = card(id = "x", name = "Banned", legalityModern = "banned")

        val result = InspirationSelection.add(emptyList(), banned, DeckFormat.MODERN)

        assertEquals(SelectionOutcome.Illegal, result.outcome)
        assertTrue(result.picks.isEmpty())
    }

    @Test
    fun `the copy cap is counted by name across printings`() {
        var picks = emptyList<InspirationPick>()
        repeat(3) { picks = InspirationSelection.add(picks, bolt, DeckFormat.MODERN).picks }
        picks = InspirationSelection.add(picks, boltPromo, DeckFormat.MODERN).picks

        assertEquals(1, picks.size)
        assertEquals(4, picks.single().quantity)
        assertEquals("b1", picks.single().card.scryfallId)
        val capped = InspirationSelection.add(picks, boltPromo, DeckFormat.MODERN)
        assertEquals(SelectionOutcome.CopyCapReached(4), capped.outcome)
    }

    @Test
    fun `the total cap is the deck size`() {
        val picks = (1..60).map { InspirationPick(card(id = "c$it", name = "Card $it"), 1) }

        val result = InspirationSelection.add(picks, bolt, DeckFormat.MODERN)

        assertIs<SelectionOutcome.TotalCapReached>(result.outcome)
    }

    @Test
    fun `basics have no per-card cap`() {
        var picks = emptyList<InspirationPick>()
        repeat(12) { picks = InspirationSelection.add(picks, mountain, DeckFormat.MODERN).picks }

        assertEquals(12, InspirationSelection.quantityOf(picks, mountain))
    }

    @Test
    fun `decrement drops the entry at zero`() {
        val picks = listOf(InspirationPick(bolt, 2))

        val once = InspirationSelection.decrement(picks, boltPromo)
        val twice = InspirationSelection.decrement(once, bolt)

        assertEquals(1, once.single().quantity)
        assertTrue(twice.isEmpty())
    }

    @Test
    fun `addMissing never bumps or drops an already selected piece`() {
        val sink = card(id = "s1", name = "Sink")
        val picks = listOf(InspirationPick(bolt, 3))

        val result = InspirationSelection.addMissing(picks, listOf(boltPromo, sink), DeckFormat.MODERN)
        val again = InspirationSelection.addMissing(result.picks, listOf(bolt, sink), DeckFormat.MODERN)

        assertEquals(SelectionOutcome.Changed, result.outcome)
        assertEquals(3, InspirationSelection.quantityOf(result.picks, bolt))
        assertEquals(1, InspirationSelection.quantityOf(result.picks, sink))
        assertEquals(SelectionOutcome.Unchanged, again.outcome)
        assertEquals(result.picks, again.picks)
    }

    @Test
    fun `addMissing reports the first rejection but keeps the valid additions`() {
        val banned = card(id = "x", name = "Banned", legalityModern = "banned")

        val result = InspirationSelection.addMissing(emptyList(), listOf(banned, bolt), DeckFormat.MODERN)

        assertEquals(SelectionOutcome.Illegal, result.outcome)
        assertEquals(listOf("Lightning Bolt"), result.picks.map { it.card.name })
    }
}
