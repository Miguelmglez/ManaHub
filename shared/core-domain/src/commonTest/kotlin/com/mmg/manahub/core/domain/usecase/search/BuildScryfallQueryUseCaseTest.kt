package com.mmg.manahub.core.domain.usecase.search

import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.ColorMatchMode
import com.mmg.manahub.core.model.SearchCriterion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Coverage for [BuildScryfallQueryUseCase]'s `SearchCriterion.CardFunction` and colour branches.
 * [SearchCriterion] is a sealed class with no `else` branch in [BuildScryfallQueryUseCase.buildPart]
 * — that `when` is itself the compile gate that forced those branches to exist; these tests assert
 * their exact output shape (not just "it compiles").
 */
class BuildScryfallQueryUseCaseTest {

    private val useCase = BuildScryfallQueryUseCase()

    @Test
    fun `given single function match-any when built then wraps in parens with no comma tail`() {
        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.CardFunction(setOf("ramp"), matchAll = false))
        )

        assertEquals("(function:ramp)", useCase(query))
    }

    @Test
    fun `given multiple functions match-any when built then comma-joined inside one paren group`() {
        val query = AdvancedSearchQuery(
            criteria = listOf(
                SearchCriterion.CardFunction(setOf("spot-removal", "board-wipe"), matchAll = false)
            )
        )

        val result = useCase(query)
        // Set iteration order isn't guaranteed — assert the shape, not a fixed string.
        assertTrue(result.startsWith("(") && result.endsWith(")"))
        assertTrue(result.contains("function:spot-removal"))
        assertTrue(result.contains("function:board-wipe"))
        assertTrue(result.contains(","))
        assertEquals(1, result.count { it == ',' })
    }

    @Test
    fun `given multiple functions match-all when built then space-joined with no parens or commas`() {
        val query = AdvancedSearchQuery(
            criteria = listOf(
                SearchCriterion.CardFunction(setOf("tutor", "ramp"), matchAll = true)
            )
        )

        val result = useCase(query)
        assertTrue(result.contains("function:tutor"))
        assertTrue(result.contains("function:ramp"))
        assertTrue(result.contains(" "))
        assertEquals(0, result.count { it == ',' })
        assertEquals(false, result.startsWith("("))
    }

    @Test
    fun `given empty function set when built then criterion contributes nothing`() {
        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.CardFunction(emptySet(), matchAll = false))
        )

        assertEquals("", useCase(query))
    }

    @Test
    fun `given curated function value when built then it is NOT run through escapeValue`() {
        // Curated values are already [a-z-]; this test documents the "never escape" contract by
        // asserting a hyphenated value survives verbatim (escapeValue would strip nothing here
        // either way, but the point is no quoting/escaping logic runs at all).
        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.CardFunction(setOf("counters-matter"), matchAll = false))
        )

        assertEquals("(function:counters-matter)", useCase(query))
    }

    @Test
    fun `given CardFunction combined with another criterion when built then joined with a space`() {
        val query = AdvancedSearchQuery(
            criteria = listOf(
                SearchCriterion.CardFunction(setOf("ramp"), matchAll = false),
                SearchCriterion.CardType(setOf("creature"), matchAll = true),
            )
        )

        assertEquals("(function:ramp) (t:creature)", useCase(query))
    }

    // ── Color rendering ───────────────────────────────────────────────────────
    //  Every case here has a truth-table twin in AdvancedSearchCardMatcherTest; the two must agree
    //  or the All Cards tab and the Collection tab return different cards for the same query.

    @Test
    fun `given ANY_OF with two colors when built then an OR group of at-least clauses`() {
        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.Colors(setOf("W", "U"), ColorMatchMode.ANY_OF))
        )

        assertEquals("(c>=w or c>=u)", useCase(query))
    }

    @Test
    fun `given ANY_OF with a single color when built then it collapses to one clause`() {
        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.Colors(setOf("W"), ColorMatchMode.ANY_OF))
        )

        assertEquals("c>=w", useCase(query))
    }

    @Test
    fun `given ANY_OF on color identity when built then the id prefix is used`() {
        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.ColorIdentity(setOf("W", "U"), ColorMatchMode.ANY_OF))
        )

        assertEquals("(id>=w or id>=u)", useCase(query))
    }

    @Test
    fun `given colorless alone when built then it renders the equality clause under every mode`() {
        // `c>=c` is not meaningful on Scryfall; colorless is only ever an equality test.
        ColorMatchMode.entries.forEach { mode ->
            val colors = AdvancedSearchQuery(criteria = listOf(SearchCriterion.Colors(setOf("C"), mode)))
            assertEquals("c=c", useCase(colors), "colors mode $mode")

            val identity = AdvancedSearchQuery(criteria = listOf(SearchCriterion.ColorIdentity(setOf("C"), mode)))
            assertEquals("id=c", useCase(identity), "identity mode $mode")
        }
    }

    @Test
    fun `given colorless mixed with a real color when built then only ANY_OF keeps it`() {
        val any = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.Colors(setOf("W", "C"), ColorMatchMode.ANY_OF))
        )
        assertEquals("(c>=w or c=c)", useCase(any))

        // A set comparison drops it -- `c>=wc` is not a valid Scryfall color string.
        val atLeast = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.Colors(setOf("W", "C"), ColorMatchMode.AT_LEAST))
        )
        assertEquals("c>=w", useCase(atLeast))

        val atMost = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.ColorIdentity(setOf("W", "C"), ColorMatchMode.AT_MOST))
        )
        assertEquals("id<=w", useCase(atMost))
    }

    @Test
    fun `given the non-OR modes when built then each renders its explicit operator`() {
        val wu = setOf("W", "U")
        assertEquals("c>=wu", useCase(AdvancedSearchQuery(criteria = listOf(SearchCriterion.Colors(wu, ColorMatchMode.AT_LEAST)))))
        assertEquals("c=wu", useCase(AdvancedSearchQuery(criteria = listOf(SearchCriterion.Colors(wu, ColorMatchMode.EXACTLY)))))
        assertEquals("id<=wu", useCase(AdvancedSearchQuery(criteria = listOf(SearchCriterion.ColorIdentity(wu, ColorMatchMode.AT_MOST)))))
    }

    @Test
    fun `given an empty color set when built then the criterion contributes nothing`() {
        val query = AdvancedSearchQuery(
            criteria = listOf(SearchCriterion.Colors(emptySet(), ColorMatchMode.ANY_OF))
        )

        assertEquals("", useCase(query))
    }
}
