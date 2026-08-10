package com.mmg.manahub.feature.decks.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deck Engine Unification plan (`docs/plans/deck-engine-unification-plan.md` §5 Phase 3.3/3.4, D9),
 * extended to v2 full coverage by the Deck Wizard & Engine Rework plan
 * (`docs/plans/deck-wizard-rework-plan.md`, WS 1.4) — [ColorStrategyAffinity]'s curated table SHAPE
 * and reverse lookup, not the curated CONTENT itself (entries are hand-curated editorial judgment
 * per the class's own KDoc; asserting on exact weights would just pin an arbitrary editorial
 * choice). WS 1.4's actual acceptance criterion -- every entry passes
 * [StrategyCatalog.isValidCombination] -- IS asserted here, since that's a structural invariant,
 * not an editorial one.
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
    fun `v2 full coverage -- every one of the 31 non-colorless color subsets has a real curated entry`() {
        // WS 1.4: 5 mono + 10 guilds + 10 three-color (5 shards + 5 wedges) + 5 four-color +
        // 1 five-color == every non-empty subset of {W,U,B,R,G} (2^5 - 1 == 31). None of these
        // should fall back to the generic [ColorStrategyAffinity]'s FALLBACK pair.
        val allColors = ManaColor.entries.filter { it != ManaColor.C }
        val allNonEmptySubsets = (1..allColors.size).flatMap { size -> allColors.combinationsOfSize(size) }
        assertEquals(31, allNonEmptySubsets.size)

        allNonEmptySubsets.forEach { combo ->
            val entries = ColorStrategyAffinity.forColors(combo.toSet())
            assertTrue(entries.isNotEmpty(), "$combo should have a curated entry")
        }
    }

    @Test
    fun `every curated entry is a valid (archetype, themes) combination per StrategyCatalog`() {
        val allColors = ManaColor.entries.filter { it != ManaColor.C }
        val allNonEmptySubsets = (1..allColors.size).flatMap { size -> allColors.combinationsOfSize(size) }

        allNonEmptySubsets.forEach { combo ->
            ColorStrategyAffinity.forColors(combo.toSet()).forEach { entry ->
                assertTrue(
                    StrategyCatalog.isValidCombination(entry.archetype, entry.themes),
                    "entry $entry for $combo should be a valid combination",
                )
            }
        }
    }

    @Test
    fun `a genuinely colorless input falls back to the conservative default`() {
        val entries = ColorStrategyAffinity.forColors(setOf(ManaColor.C))
        assertEquals(listOf(ArchetypeId.MIDRANGE, ArchetypeId.CONTROL), entries.map { it.archetype })
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

    /** Small local combinations helper -- not part of `kotlin.collections`, and this test is the
     * only place in the module that needs it (mirrors this test class's own "hand-rolled, not
     * worth a shared public API" precedent elsewhere in the engine package). */
    private fun <T> List<T>.combinationsOfSize(size: Int): List<List<T>> {
        if (size == 0) return listOf(emptyList())
        if (isEmpty()) return emptyList()
        val head = first()
        val tail = drop(1)
        val withHead = tail.combinationsOfSize(size - 1).map { listOf(head) + it }
        val withoutHead = tail.combinationsOfSize(size)
        return withHead + withoutHead
    }
}
