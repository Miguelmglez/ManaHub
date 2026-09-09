package com.mmg.manahub.feature.decks.presentation.wizard

import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.CardSection
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.template.CollectionProfile
import com.mmg.manahub.feature.decks.domain.template.OwnedCommanderCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // ── computeOwnedAvailabilityBySection (Deck Wizard Commander v3 plan, Phase 5, 5.1) ─────────
    // Every classification signal below is the REAL engine (ArchetypeRoleClassifier/PlacementScorer/
    // TribeDeriver/BasicLandCalculator) -- no mocking, per the campaign's own "attribution comes only
    // from the engine" rule.

    @Test
    fun `a role-tagged owned card counts toward its own role section, excluded ids don't count`() {
        val rampCard = card(id = "ramp-1", name = "Ramp Spell", colorIdentity = listOf("G"), tags = listOf(CardTag("ramp", TagCategory.ROLE)))
        val excludedCopy = card(id = "ramp-2", name = "Already Added", colorIdentity = listOf("G"), tags = listOf(CardTag("ramp", TagCategory.ROLE)))
        val sections = listOf(CardSection(id = "role:ramp", label = "Ramp", current = 0, min = 8, ideal = 10, max = 14))

        val availability = computeOwnedAvailabilityBySection(
            sections = sections,
            ownedCards = listOf(rampCard, excludedCopy),
            excludeIds = setOf(excludedCopy.scryfallId),
            identity = setOf(ManaColor.G),
            format = DeckFormat.COMMANDER,
        )

        assertEquals(1, availability["role:ramp"])
    }

    @Test
    fun `a card whose colors sit outside the identity is never counted (identity rejection)`() {
        val offColor = card(id = "off-1", name = "Off Color", colorIdentity = listOf("U"), tags = listOf(CardTag("ramp", TagCategory.ROLE)))
        val sections = listOf(CardSection(id = "role:ramp", label = "Ramp", current = 0, min = 8, ideal = 10, max = 14))

        val availability = computeOwnedAvailabilityBySection(
            sections = sections,
            ownedCards = listOf(offColor),
            excludeIds = emptySet(),
            identity = setOf(ManaColor.G),
            format = DeckFormat.COMMANDER,
        )

        assertTrue(availability.isEmpty())
    }

    @Test
    fun `a card banned in strict Commander is never counted, even if identity-legal`() {
        val banned = card(id = "banned-1", name = "Banned Ramp", colorIdentity = listOf("G"), tags = listOf(CardTag("ramp", TagCategory.ROLE)), legalityCommander = "banned")
        val sections = listOf(CardSection(id = "role:ramp", label = "Ramp", current = 0, min = 8, ideal = 10, max = 14))

        val availability = computeOwnedAvailabilityBySection(
            sections = sections,
            ownedCards = listOf(banned),
            excludeIds = emptySet(),
            identity = setOf(ManaColor.G),
            format = DeckFormat.COMMANDER,
        )

        assertTrue(availability.isEmpty())
    }

    @Test
    fun `a section id with no matching signal (offplan) is absent from the map, never zero`() {
        val plainCard = card(id = "plain-1", name = "Plain Card", colorIdentity = listOf("G"))
        val sections = listOf(CardSection(id = "offplan", label = "Off-plan", current = 0))

        val availability = computeOwnedAvailabilityBySection(
            sections = sections,
            ownedCards = listOf(plainCard),
            excludeIds = emptySet(),
            identity = setOf(ManaColor.G),
            format = DeckFormat.COMMANDER,
        )

        assertNull(availability["offplan"])
        assertTrue(availability.isEmpty())
    }

    @Test
    fun `a card can match multiple sections at once (role + curve bucket)`() {
        val multiCard = card(id = "multi-1", name = "Multi Match", colorIdentity = listOf("G"), cmc = 2.0, tags = listOf(CardTag("ramp", TagCategory.ROLE)))
        val sections = listOf(
            CardSection(id = "role:ramp", label = "Ramp", current = 0, min = 8, ideal = 10, max = 14),
            CardSection(id = "mv:2", label = "2 Cost", current = 0),
        )

        val availability = computeOwnedAvailabilityBySection(
            sections = sections,
            ownedCards = listOf(multiCard),
            excludeIds = emptySet(),
            identity = setOf(ManaColor.G),
            format = DeckFormat.COMMANDER,
        )

        assertEquals(1, availability["role:ramp"])
        assertEquals(1, availability["mv:2"])
    }
}
