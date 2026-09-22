package com.mmg.manahub.core.domain.search

import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.ColorMatchMode
import com.mmg.manahub.core.model.ComparisonOperator
import com.mmg.manahub.core.model.FriendCardSearchParams
import com.mmg.manahub.core.model.SearchCriterion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FriendCardSearchMapperTest {

    private fun params(vararg criteria: SearchCriterion, name: String = "", exact: Boolean = false) =
        FriendCardSearchMapper.toParams(name, exact, AdvancedSearchQuery(criteria.toList()))

    @Test
    fun `empty input maps to empty params with nulls, not empty arrays`() {
        val p = params()
        assertTrue(p.isEmpty())
        assertNull(p.typesAll)
        assertNull(p.colors)
        assertNull(p.formats)
    }

    @Test
    fun `name is trimmed, whitespace collapsed and capped at 100 chars`() {
        assertEquals("lightning bolt", params(name = "  lightning   bolt ").name)
        assertEquals(100, params(name = "x".repeat(250)).name?.length)
    }

    @Test
    fun `one-character name is not sent`() {
        val p = params(name = " a ")
        assertNull(p.name)
        assertTrue(p.isEmpty())
    }

    @Test
    fun `exact flag is dropped when there is no effective name`() {
        assertFalse(params(name = "a", exact = true).nameExact)
        assertTrue(params(name = "Sol Ring", exact = true).nameExact)
    }

    @Test
    fun `name criterion is used only when search text is blank`() {
        assertEquals("Opt", params(SearchCriterion.Name("Opt", exact = true)).name)
        assertTrue(params(SearchCriterion.Name("Opt", exact = true)).nameExact)
        assertEquals("Bolt", params(SearchCriterion.Name("Opt"), name = "Bolt").name)
    }

    @Test
    fun `oracle text is trimmed and blank becomes null`() {
        assertEquals("draw a card", params(SearchCriterion.OracleText("  draw a card ")).oracleText)
        assertNull(params(SearchCriterion.OracleText("   ")).oracleText)
    }

    @Test
    fun `card types split into all, any and exclude`() {
        val p = params(
            SearchCriterion.CardType(setOf("Creature", "Legendary"), matchAll = true),
            SearchCriterion.CardType(setOf("Instant", "Sorcery"), matchAll = false),
            SearchCriterion.CardType(setOf("Land"), exclude = true),
        )
        assertEquals(listOf("Creature", "Legendary"), p.typesAll)
        assertEquals(listOf("Instant", "Sorcery"), p.typesAny)
        assertEquals(listOf("Land"), p.typesExclude)
    }

    @Test
    fun `colors keep mode and drop invalid letters`() {
        val p = params(
            SearchCriterion.Colors(setOf("u", "W", "X"), ColorMatchMode.EXACTLY),
            SearchCriterion.ColorIdentity(setOf("C"), ColorMatchMode.ANY_OF),
        )
        assertEquals(listOf("W", "U"), p.colors)
        assertEquals(ColorMatchMode.EXACTLY, p.colorsMode)
        assertEquals(listOf("C"), p.identity)
        assertEquals(ColorMatchMode.ANY_OF, p.identityMode)
    }

    @Test
    fun `empty color set is no constraint and keeps the default mode`() {
        val p = params(SearchCriterion.Colors(emptySet(), ColorMatchMode.EXACTLY))
        assertNull(p.colors)
        assertEquals(FriendCardSearchParams().colorsMode, p.colorsMode)
    }

    @Test
    fun `comparison operators translate to inclusive min and max`() {
        assertEquals(null to 2, params(SearchCriterion.ManaCost(3, ComparisonOperator.LESS)).let { it.mvMin to it.mvMax })
        assertEquals(null to 3, params(SearchCriterion.ManaCost(3, ComparisonOperator.LESS_OR_EQUAL)).let { it.mvMin to it.mvMax })
        assertEquals(3 to 3, params(SearchCriterion.ManaCost(3, ComparisonOperator.EQUAL)).let { it.mvMin to it.mvMax })
        assertEquals(3 to null, params(SearchCriterion.Power(3, ComparisonOperator.GREATER_OR_EQUAL)).let { it.powerMin to it.powerMax })
        assertEquals(4 to null, params(SearchCriterion.Toughness(3, ComparisonOperator.GREATER)).let { it.toughnessMin to it.toughnessMax })
    }

    @Test
    fun `repeated range criteria intersect`() {
        val p = params(
            SearchCriterion.ManaCost(2, ComparisonOperator.GREATER_OR_EQUAL),
            SearchCriterion.ManaCost(5, ComparisonOperator.LESS),
        )
        assertEquals(2, p.mvMin)
        assertEquals(4, p.mvMax)
    }

    @Test
    fun `not-equal is unsupported and ignored`() {
        val criterion = SearchCriterion.ManaCost(3, ComparisonOperator.NOT_EQUAL)
        assertFalse(FriendCardSearchMapper.isSupported(criterion))
        assertTrue(params(criterion).isEmpty())
    }

    @Test
    fun `rarity, set and language are lowercased and deduplicated`() {
        val p = params(
            SearchCriterion.Rarity(listOf("Rare", "rare", "Mythic")),
            SearchCriterion.CardSet(setOf("MH3", "neo")),
            SearchCriterion.Language("JA"),
        )
        assertEquals(listOf("rare", "mythic"), p.rarities)
        assertEquals(listOf("mh3", "neo"), p.setCodes)
        assertEquals(listOf("ja"), p.languages)
    }

    @Test
    fun `formats are lowercased, invalid ids dropped and legality flag kept`() {
        val p = params(SearchCriterion.Format(listOf("Commander", "pre-modern", "x"), legal = false))
        assertEquals(listOf("commander"), p.formats)
        assertFalse(p.formatLegal)
        assertNull(params(SearchCriterion.Format(listOf("!!"))).formats)
    }

    @Test
    fun `arrays are capped at 50 and oversize elements dropped`() {
        val many = (1..80).map { "set$it" }.toSet() + ("y".repeat(101))
        val p = params(SearchCriterion.CardSet(many))
        assertEquals(50, p.setCodes?.size)
        assertTrue(p.setCodes!!.none { it.length > 100 })
    }

    @Test
    fun `unsupported criteria are removed by supportedOnly`() {
        val query = AdvancedSearchQuery(
            listOf(
                SearchCriterion.HasTag(listOf("ramp")),
                SearchCriterion.Price(5.0, "usd"),
                SearchCriterion.CardFunction(setOf("removal")),
                SearchCriterion.Artist("x"),
                SearchCriterion.CommanderEligible,
                SearchCriterion.Rarity(listOf("rare")),
            )
        )
        assertEquals(listOf<SearchCriterion>(SearchCriterion.Rarity(listOf("rare"))), FriendCardSearchMapper.supportedOnly(query).criteria)
    }

    @Test
    fun `type and format arrays are capped at 10 while other arrays keep 50`() {
        val types = (1..30).map { "Type$it" }.toSet()
        val formats = listOf("aa", "bb", "cc", "dd", "ee", "ff", "gg", "hh", "ii", "jj", "kk", "ll")
        val p = params(
            SearchCriterion.CardType(types, matchAll = true),
            SearchCriterion.CardType(types, matchAll = false),
            SearchCriterion.CardType(types, exclude = true),
            SearchCriterion.Format(formats),
            SearchCriterion.CardSet((1..30).map { "s$it" }.toSet()),
        )
        assertEquals(10, p.typesAll?.size)
        assertEquals(10, p.typesAny?.size)
        assertEquals(10, p.typesExclude?.size)
        assertEquals(10, p.formats?.size)
        assertEquals(30, p.setCodes?.size)
    }

    @Test
    fun `invalid formats do not consume the format cap`() {
        val formats = List(12) { "!bad" } + "modern"
        assertEquals(listOf("modern"), params(SearchCriterion.Format(formats)).formats)
    }

    @Test
    fun `extreme numeric values are clamped instead of overflowing`() {
        val high = params(SearchCriterion.ManaCost(Int.MAX_VALUE, ComparisonOperator.GREATER))
        assertEquals(FriendCardSearchMapper.MAX_NUMERIC_VALUE + 1, high.mvMin)
        val low = params(SearchCriterion.Power(Int.MIN_VALUE, ComparisonOperator.LESS))
        assertEquals(-FriendCardSearchMapper.MAX_NUMERIC_VALUE - 1, low.powerMax)
    }

    @Test
    fun `activeFilterCount counts filter groups from the params, not the name`() {
        val p = params(
            SearchCriterion.Rarity(listOf("rare")),
            SearchCriterion.ManaCost(2, ComparisonOperator.GREATER_OR_EQUAL),
            SearchCriterion.ManaCost(4, ComparisonOperator.LESS_OR_EQUAL),
            name = "bolt",
        )
        assertEquals(2, p.activeFilterCount())
    }
}
