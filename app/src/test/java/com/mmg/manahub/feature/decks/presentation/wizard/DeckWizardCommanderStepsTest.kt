package com.mmg.manahub.feature.decks.presentation.wizard

import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.template.CollectionProfile
import com.mmg.manahub.feature.decks.domain.template.OwnedCommanderCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deck Wizard Commander v3 plan, Phase 3.2 — plain (non-Composable) helpers pulled out of
 * [CommanderPickStepContent] so the grid's candidate math and the lock rules are covered by a JVM
 * unit test rather than a Compose UI test.
 */
class DeckWizardCommanderStepsTest {

    private fun emptyProfile(candidates: List<OwnedCommanderCandidate>) = CollectionProfile(
        colorShares = emptyList(),
        dominantStrategies = emptyList(),
        dominantTribes = emptyList(),
        commanderCandidates = candidates,
    )

    // ── commanderPickLocalCandidates ────────────────────────────────────────────

    @Test
    fun `the grid shows EVERY eligible owned candidate, including a non-creature 'can be your commander' card`() {
        // CommanderEligibility.isCommanderEligible already ran upstream (CollectionProfileUseCase)
        // to produce commanderCandidates -- this function must not re-filter or drop anything it
        // was handed, a non-creature commander included (D-G).
        val legendaryCreature = card(id = "lc-1", name = "Elf Lord", typeLine = "Legendary Creature — Elf")
        val planeswalkerCommander = card(
            id = "pw-1",
            name = "Comm. Planeswalker",
            typeLine = "Legendary Planeswalker — Test",
            oracleText = "Comm. Planeswalker can be your commander.",
        )
        val candidates = listOf(
            OwnedCommanderCandidate(legendaryCreature, 0.8f),
            OwnedCommanderCandidate(planeswalkerCommander, 0.7f),
        )
        val state = DeckWizardUiState(collectionProfile = emptyProfile(candidates))

        val shown = commanderPickLocalCandidates(state)

        assertEquals(setOf(legendaryCreature, planeswalkerCommander), shown.toSet())
    }

    @Test
    fun `no cap -- 93 candidates all show up, mirroring the real-collection fixture count`() {
        val candidates = (1..93).map { i ->
            OwnedCommanderCandidate(card(id = "cmd-$i", name = "Commander $i", typeLine = "Legendary Creature — Test"), 1f - i * 0.001f)
        }
        val state = DeckWizardUiState(collectionProfile = emptyProfile(candidates))

        assertEquals(93, commanderPickLocalCandidates(state).size)
    }

    @Test
    fun `the plain name filter narrows the local grid`() {
        val zada = card(id = "zada", name = "Zada, Hedron Grinder", typeLine = "Legendary Creature — Goblin Shaman")
        val meren = card(id = "meren", name = "Meren of Clan Nel Toth", typeLine = "Legendary Creature — Human Shaman")
        val state = DeckWizardUiState(
            collectionProfile = emptyProfile(listOf(OwnedCommanderCandidate(zada, 1f), OwnedCommanderCandidate(meren, 1f))),
            commanderQuery = "zada",
        )

        assertEquals(listOf(zada), commanderPickLocalCandidates(state))
    }

    @Test
    fun `no collection profile yet -- the grid is empty, not a crash`() {
        assertTrue(commanderPickLocalCandidates(DeckWizardUiState()).isEmpty())
    }

    // ── commanderPickCollectionTabResults ───────────────────────────────────────

    @Test
    fun `the Collection tab filters ownedCards by the active structured query`() {
        val eligible = card(id = "e1", name = "Eligible One", typeLine = "Legendary Creature — Test")
        val notEligible = card(id = "n1", name = "Not Eligible", typeLine = "Sorcery")
        val state = DeckWizardUiState(
            ownedCards = listOf(eligible, notEligible),
            commanderStructuredQuery = AdvancedSearchQuery(criteria = listOf(SearchCriterion.CommanderEligible)),
        )

        assertEquals(listOf(eligible), commanderPickCollectionTabResults(state))
    }

    @Test
    fun `no structured query -- the Collection-tab helper matches everything (unused in that mode)`() {
        val a = card(id = "a", name = "A")
        val b = card(id = "b", name = "B")
        assertEquals(listOf(a, b), commanderPickCollectionTabResults(DeckWizardUiState(ownedCards = listOf(a, b))))
    }

    // ── commanderLockedCriteria (D14, Casual has no legality lock) ──────────────

    @Test
    fun `Commander locks CommanderEligible AND a strict Format legality clause`() {
        val locked = commanderLockedCriteria(DeckFormat.COMMANDER)

        assertEquals(
            listOf(SearchCriterion.CommanderEligible, SearchCriterion.Format(listOf("commander"), legal = true)),
            locked,
        )
    }

    @Test
    fun `Commander Casual locks ONLY CommanderEligible -- no legality lock (R2)`() {
        val locked = commanderLockedCriteria(DeckFormat.COMMANDER_CASUAL)

        assertEquals(listOf(SearchCriterion.CommanderEligible), locked)
        assertFalse(locked.any { it is SearchCriterion.Format })
    }

    @Test
    fun `a null format (not yet resolved) still locks CommanderEligible, never a Format clause`() {
        val locked = commanderLockedCriteria(null)

        assertEquals(listOf(SearchCriterion.CommanderEligible), locked)
    }
}
