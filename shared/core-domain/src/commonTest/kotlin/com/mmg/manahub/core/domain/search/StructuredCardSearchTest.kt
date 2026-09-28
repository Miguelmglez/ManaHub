package com.mmg.manahub.core.domain.search

import com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.feature.decks.domain.engine.card
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deck Wizard Commander v3 plan, Phase 3.3 — [StructuredCardSearch] is the extracted "one query,
 * two tabs" pairing shared by Deck Studio's Analysis-tab "Browse for X" flow and the Deck Wizard's
 * commander pick step. This suite locks the contract both callers depend on.
 */
class StructuredCardSearchTest {

    private val bolt = card(id = "bolt", name = "Lightning Bolt", typeLine = "Instant", colors = listOf("R"), colorIdentity = listOf("R"))
    private val forest = card(id = "forest", name = "Forest", typeLine = "Basic Land — Forest", colors = emptyList(), colorIdentity = listOf("G"))

    @Test
    fun `an empty query renders to a blank fragment, so scryfallFragment returns null`() {
        assertNull(StructuredCardSearch.scryfallFragment(AdvancedSearchQuery()))
    }

    @Test
    fun `a non-empty query renders a real Scryfall fragment`() {
        val fragment = StructuredCardSearch.scryfallFragment(AdvancedSearchQuery(criteria = listOf(SearchCriterion.CommanderEligible)))
        assertEquals("is:commander", fragment)
    }

    @Test
    fun `a null or empty query matches every card locally`() {
        assertTrue(StructuredCardSearch.matches(bolt, null))
        assertTrue(StructuredCardSearch.matches(bolt, AdvancedSearchQuery()))
    }

    @Test
    fun `a real criterion is evaluated via AdvancedSearchCardMatcher`() {
        val query = AdvancedSearchQuery(criteria = listOf(SearchCriterion.Colors(setOf("R"))))
        assertTrue(StructuredCardSearch.matches(bolt, query))
        assertFalse(StructuredCardSearch.matches(forest, query))
    }

    @Test
    fun `collectionMatches ANDs the query with an optional name filter`() {
        val query = AdvancedSearchQuery(criteria = listOf(SearchCriterion.ColorIdentity(setOf("R", "G"))))
        val matches = StructuredCardSearch.collectionMatches(listOf(bolt, forest), query, nameFilter = "bolt")
        assertEquals(listOf(bolt), matches)
    }

    @Test
    fun `a null query with a blank name filter returns every card`() {
        assertEquals(listOf(bolt, forest), StructuredCardSearch.collectionMatches(listOf(bolt, forest), null))
    }

    @Test
    fun `an explicit BuildScryfallQueryUseCase instance is honored`() {
        val fragment = StructuredCardSearch.scryfallFragment(
            AdvancedSearchQuery(criteria = listOf(SearchCriterion.CommanderEligible)),
            BuildScryfallQueryUseCase(),
        )
        assertEquals("is:commander", fragment)
    }
}
