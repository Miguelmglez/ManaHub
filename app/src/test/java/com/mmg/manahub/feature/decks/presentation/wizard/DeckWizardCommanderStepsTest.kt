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

    // ── commanderPickIsIdle / commanderActiveFilterCount / buildCommanderSearchQuery (W2.1, G2/R2) ──

    @Test
    fun `idle -- no query text and no active filter`() {
        val locked = commanderLockedCriteria(DeckFormat.COMMANDER)
        val state = DeckWizardUiState(commanderQuery = "", commanderStructuredQuery = null)

        assertTrue(commanderPickIsIdle(state, locked))
        assertNull(buildCommanderSearchQuery(state, locked))
    }

    @Test
    fun `a structured query containing ONLY the locked criteria still counts as idle`() {
        val locked = commanderLockedCriteria(DeckFormat.COMMANDER)
        val state = DeckWizardUiState(commanderStructuredQuery = AdvancedSearchQuery(criteria = locked))

        assertEquals(0, commanderActiveFilterCount(state.commanderStructuredQuery, locked))
        assertTrue(commanderPickIsIdle(state, locked))
    }

    @Test
    fun `non-blank query text ends idle and builds a query carrying a Name criterion`() {
        val locked = commanderLockedCriteria(DeckFormat.COMMANDER)
        val state = DeckWizardUiState(commanderQuery = "Zada")

        assertFalse(commanderPickIsIdle(state, locked))
        val built = buildCommanderSearchQuery(state, locked)
        assertEquals(SearchCriterion.Name("Zada"), built?.criteria?.last())
        assertTrue(locked.all { l -> built!!.criteria.any { it::class == l::class } })
    }

    @Test
    fun `a user-added filter beyond the locked set ends idle`() {
        val locked = commanderLockedCriteria(DeckFormat.COMMANDER)
        val userCriterion = SearchCriterion.ColorIdentity(setOf("G"))
        val state = DeckWizardUiState(commanderStructuredQuery = AdvancedSearchQuery(criteria = locked + userCriterion))

        assertEquals(1, commanderActiveFilterCount(state.commanderStructuredQuery, locked))
        assertFalse(commanderPickIsIdle(state, locked))
        val built = buildCommanderSearchQuery(state, locked)
        assertTrue(built!!.criteria.contains(userCriterion))
    }

    @Test
    fun `locked criteria survive a clear-and-repeat-search cycle -- never droppable`() {
        val locked = commanderLockedCriteria(DeckFormat.COMMANDER)
        val state = DeckWizardUiState(commanderQuery = "Meren", commanderStructuredQuery = null)

        val built = buildCommanderSearchQuery(state, locked)!!

        assertTrue(locked.all { l -> built.criteria.any { it::class == l::class } })
    }

    // ── commanderOwnedIds (W2.1, G2 -- Scryfall results still show ownership) ────

    @Test
    fun `commanderOwnedIds matches Scryfall results by scryfallId`() {
        val owned = card(id = "owned-1", name = "Owned Commander")
        val ownedIds = commanderOwnedIds(listOf(owned))

        assertTrue("owned-1" in ownedIds)
        assertFalse("unowned-1" in ownedIds)
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

    // ── choiceDisplayIds (W7 Task B, R10) ───────────────────────────────────────

    @Test
    fun `tentative defaults always show, even when the cap would otherwise hide them`() {
        // A pathological cap of 0 alternatives must still show every tentative default -- the cap
        // only ever truncates ALTERNATIVES, never the engine's own pre-selected picks.
        val ids = choiceDisplayIds(tentativeIds = listOf("tent-1", "tent-2"), alternativeIds = listOf("alt-1", "alt-2"), cap = 0)
        assertEquals(listOf("tent-1", "tent-2"), ids)
    }

    @Test
    fun `alternatives beyond the cap are truncated, preserving gain order`() {
        val ids = choiceDisplayIds(tentativeIds = listOf("tent-1"), alternativeIds = listOf("alt-1", "alt-2", "alt-3"), cap = 2)
        assertEquals(listOf("tent-1", "alt-1", "alt-2"), ids)
    }

    @Test
    fun `an id present in both lists is never duplicated`() {
        val ids = choiceDisplayIds(tentativeIds = listOf("tent-1"), alternativeIds = listOf("tent-1", "alt-1"), cap = 10)
        assertEquals(listOf("tent-1", "alt-1"), ids)
    }
}
