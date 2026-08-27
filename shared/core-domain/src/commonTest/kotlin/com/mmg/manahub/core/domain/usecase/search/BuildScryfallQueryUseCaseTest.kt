package com.mmg.manahub.core.domain.usecase.search

import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.SearchCriterion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Coverage for [BuildScryfallQueryUseCase]'s new `SearchCriterion.CardFunction` branch (Deck
 * Analysis — Category Sections plan, W4). [SearchCriterion] is a sealed class with no `else` branch
 * in [BuildScryfallQueryUseCase.buildPart] — that `when` is itself the compile gate that forced this
 * branch to exist; these tests assert its exact output shape (not just "it compiles").
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
}
