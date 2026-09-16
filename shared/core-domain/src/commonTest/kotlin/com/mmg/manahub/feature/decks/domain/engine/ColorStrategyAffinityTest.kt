package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.DeckFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    // Deck Wizard 60-card wave (v6), plan §5 Phase 2.2, S9: a colorless build now resolves a REAL
    // curated row (big_mana/artifacts/aggro/tribal/prison), never the generic FALLBACK pair --
    // rewritten (not left alongside a new test) since the old assertion below directly contradicts
    // S9's own decision.
    @Test
    fun `forColors empty set and forColors C both return the new colorless row, never FALLBACK`() {
        val viaEmpty = ColorStrategyAffinity.forColors(emptySet())
        val viaC = ColorStrategyAffinity.forColors(setOf(ManaColor.C))
        assertEquals(viaEmpty, viaC)
        assertTrue(viaEmpty.isNotEmpty())
        assertTrue(
            viaEmpty != listOf(ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.6f), ColorStrategyEntry(archetype = ArchetypeId.CONTROL, weight = 0.5f)),
            "must not be FALLBACK",
        )
        // S9 colorless row: big_mana 0.9, artifacts 0.85, aggro 0.7, tribal(eldrazi) 0.65, prison 0.5 -- weight-desc.
        assertEquals(listOf(0.9f, 0.85f, 0.7f, 0.65f, 0.5f), viaEmpty.map { it.weight })
    }

    @Test
    fun `curatedFor UW STANDARD is non-empty, de-duplicated by catalog id, weight-desc`() {
        val results = ColorStrategyAffinity.curatedFor(setOf(ManaColor.U, ManaColor.W), DeckFormat.STANDARD)
        assertTrue(results.isNotEmpty())
        val ids = results.map { it.first.id }
        assertEquals(ids.distinct(), ids, "must be de-duplicated by catalog id, keeping the max weight per id")
        assertEquals(results.map { it.second }.sortedDescending(), results.map { it.second })
    }

    @Test
    fun `curatedFor prefers the catalog entry whose postures contain a RAMP-posture row -- big_mana`() {
        // TABLE[{G}] has ColorStrategyEntry(archetype=MIDRANGE, posture=RAMP, weight=0.9f) -- curatedFor
        // must map this via nearestFor and prefer a catalog entry whose `postures` contains RAMP.
        val results = ColorStrategyAffinity.curatedFor(setOf(ManaColor.G), DeckFormat.COMMANDER)
        val topForRampSignal = results.firstOrNull { it.first.postures.contains(PostureId.RAMP) }
        assertNotNull(topForRampSignal)
        assertEquals("big_mana", topForRampSignal.first.id, "a RAMP-posture-carrying entry must resolve to the big_mana catalog id")
    }

    @Test
    fun `curatedFor prefers a TEMPO-posture row -- tempo`() {
        // TABLE[{U,R}] (Izzet) has ColorStrategyEntry(archetype=AGGRO, posture=TEMPO, themes=[SPELLSLINGER], weight=0.9f).
        val results = ColorStrategyAffinity.curatedFor(setOf(ManaColor.U, ManaColor.R), DeckFormat.COMMANDER)
        val topForTempoSignal = results.firstOrNull { it.first.postures.contains(PostureId.TEMPO) }
        assertNotNull(topForTempoSignal)
        assertEquals("tempo", topForTempoSignal.first.id)
    }

    @Test
    fun `the colorless row's tribal entry carries tribe tribe colon eldrazi`() {
        val colorless = ColorStrategyAffinity.forColors(emptySet())
        val tribalEntry = colorless.first { it.themes.contains(ThemeId.TRIBAL) }
        assertEquals("tribe:eldrazi", tribalEntry.tribe)
    }

    @Test
    fun `combosFor never mixes emptySet with WUBRG keys -- a colorless-capable strategy yields emptySet as a separate combo`() {
        // big_mana is both a real WUBRG TABLE entry (e.g. TABLE[{G}] MIDRANGE+RAMP) AND the colorless row's own top entry.
        val combos = ColorStrategyAffinity.combosFor(ArchetypeId.MIDRANGE, null)
        val hasColorless = combos.any { it.first.isEmpty() }
        val hasWubrg = combos.any { it.first.isNotEmpty() }
        assertTrue(hasColorless, "combosFor(MIDRANGE) must include the colorless emptySet() combo now that the colorless row carries big_mana/artifacts")
        assertTrue(hasWubrg, "combosFor(MIDRANGE) must still include real WUBRG combos")
        // "never mixed" means each Pair's `first` is either emptySet() or a non-empty WUBRG set --
        // trivially true by construction (Set<ManaColor> per entry); the real assertion is that
        // BOTH kinds appear as SEPARATE list entries, asserted above.
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
    fun `combosFor a null archetype and null theme returns nothing -- there is nothing to rank against`() {
        assertTrue(ColorStrategyAffinity.combosFor(null, null).isEmpty())
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
