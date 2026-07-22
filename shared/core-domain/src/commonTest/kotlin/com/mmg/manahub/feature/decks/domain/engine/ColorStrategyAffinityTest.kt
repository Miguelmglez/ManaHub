package com.mmg.manahub.feature.decks.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deck Engine Unification plan (`docs/plans/deck-engine-unification-plan.md` §5 Phase 3.3/3.4, D9) —
 * [ColorStrategyAffinity]'s curated table shape and reverse lookup, not the curated CONTENT itself
 * (the entries are a hand-picked v1 seed per the class's own KDoc, explicitly not authoritative --
 * asserting on exact weights would just pin an arbitrary editorial choice).
 */
class ColorStrategyAffinityTest {

    @Test
    fun `every mono color has at least one curated entry`() {
        ManaColor.entries.filter { it != ManaColor.C }.forEach { color ->
            val entries = ColorStrategyAffinity.forColors(setOf(color))
            assertTrue(entries.isNotEmpty(), "mono-$color should have at least one curated strategy")
        }
    }

    @Test
    fun `entries are sorted best-first by weight`() {
        val entries = ColorStrategyAffinity.forColors(setOf(ManaColor.R))
        val weights = entries.map { it.weight }
        assertEquals(weights.sortedDescending(), weights)
    }

    @Test
    fun `an unlisted combination falls back to a non-empty conservative list`() {
        // 4-color is not in the curated table -- must still return something (never dead-end Flow B).
        val entries = ColorStrategyAffinity.forColors(setOf(ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.R))
        assertTrue(entries.isNotEmpty())
    }

    @Test
    fun `combosFor with both null returns nothing to rank`() {
        assertTrue(ColorStrategyAffinity.combosFor(null, null).isEmpty())
    }

    @Test
    fun `combosFor an archetype returns every color combo that lists it, sorted best-first`() {
        val combos = ColorStrategyAffinity.combosFor(ArchetypeId.AGGRO, null)
        assertTrue(combos.isNotEmpty())
        val weights = combos.map { it.second }
        assertEquals(weights.sortedDescending(), weights)
        // Mono-Red is a curated AGGRO entry -- must be among the results.
        assertTrue(combos.any { it.first == setOf(ManaColor.R) })
    }

    @Test
    fun `combosFor GENERIC (the neutral default) returns nothing -- it is never a curated pick`() {
        assertTrue(ColorStrategyAffinity.combosFor(ArchetypeId.GENERIC, null).isEmpty())
    }

    @Test
    fun `combosFor a theme returns every color combo that lists it`() {
        val combos = ColorStrategyAffinity.combosFor(null, ThemeId.ARISTOCRATS)
        assertTrue(combos.isNotEmpty())
    }
}
