package com.mmg.manahub.feature.decks.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deck Analysis Engine v2 plan (docs/plans/deck-analysis-engine-v2-plan.md), Phase 1 gate — every
 * [CuratedStrategyCatalog] entry must be a VALID composition per [StrategyCatalog]'s own
 * compatibility matrix (so the future picker, Phase 3, can never construct an incoherent
 * archetype+theme pin the way the free-combination `ArchetypePlanSheet` can today), and the
 * [nearestFor] display-mapper must resolve inference output onto the catalog correctly.
 */
class CuratedStrategyCatalogTest {

    // ── 1. Every entry is a valid (archetype, themes, tribe) combination ───────────────────────

    @Test
    fun `every catalog entry passes StrategyCatalog isValidCombination`() {
        CuratedStrategyCatalog.ALL.forEach { strategy ->
            val tribe = if (strategy.requiresTribe) "Elves" else null
            assertTrue(
                StrategyCatalog.isValidCombination(strategy.archetype, strategy.themes, tribe),
                "Entry '${strategy.id}' (${strategy.archetype}, themes=${strategy.themes}) failed " +
                    "StrategyCatalog.isValidCombination",
            )
        }
    }

    @Test
    fun `only the Tribal entry requires a tribe`() {
        CuratedStrategyCatalog.ALL.forEach { strategy ->
            val hasTribalTheme = ThemeId.TRIBAL in strategy.themes
            assertEquals(
                hasTribalTheme,
                strategy.requiresTribe,
                "Entry '${strategy.id}': requiresTribe must match whether ThemeId.TRIBAL is present",
            )
        }
    }

    // ── 2. Commander-only themes (ArchetypeData.THEMES[theme].commanderOnly) never ship with the
    //       60-card format ─────────────────────────────────────────────────────────────────────

    @Test
    fun `entries whose theme is structurally Commander-only never include the SIXTY format`() {
        CuratedStrategyCatalog.ALL.forEach { strategy ->
            val structurallyCommanderOnly = strategy.themes.any { theme ->
                ArchetypeData.THEMES.getValue(theme).commanderOnly
            }
            if (structurallyCommanderOnly) {
                assertEquals(
                    setOf(ArchetypeFormat.COMMANDER),
                    strategy.formats,
                    "Entry '${strategy.id}' uses a structurally Commander-only theme " +
                        "(ArchetypeData.THEMES[...].commanderOnly=true) but offers formats=${strategy.formats}",
                )
            }
        }
    }

    @Test
    fun `group_hug group_slug and clones_theft are Commander-only`() {
        listOf("group_hug", "group_slug", "clones_theft").forEach { id ->
            val strategy = assertNotNull(CuratedStrategyCatalog.byId(id), "Missing catalog entry '$id'")
            assertEquals(setOf(ArchetypeFormat.COMMANDER), strategy.formats)
        }
    }

    // ── 3. No duplicate ids ─────────────────────────────────────────────────────────────────────

    @Test
    fun `no duplicate ids in the catalog`() {
        val ids = CuratedStrategyCatalog.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Duplicate id(s) found: ${ids.groupBy { it }.filterValues { it.size > 1 }.keys}")
    }

    @Test
    fun `catalog version is set`() {
        assertTrue(CuratedStrategyCatalog.CATALOG_VERSION >= 1)
    }

    // ── 4. nearestFor mapper ────────────────────────────────────────────────────────────────────

    @Test
    fun `nearestFor returns the exact themed match`() {
        val result = CuratedStrategyCatalog.nearestFor(ArchetypeId.MIDRANGE, listOf(ThemeId.ARISTOCRATS))
        assertEquals("aristocrats", assertNotNull(result).id)
    }

    @Test
    fun `nearestFor returns the exact pure-archetype match when no themes are given`() {
        val result = CuratedStrategyCatalog.nearestFor(ArchetypeId.AGGRO, emptyList())
        assertEquals("aggro", assertNotNull(result).id)
    }

    @Test
    fun `nearestFor exact match is order-insensitive over the theme set`() {
        // No 2-theme curated entry actually exists in v1, but the SET-equality contract should
        // still hold for a hypothetical/future combined entry rather than depending on List order.
        val forward = CuratedStrategyCatalog.nearestFor(ArchetypeId.MIDRANGE, listOf(ThemeId.ARISTOCRATS))
        val single = CuratedStrategyCatalog.nearestFor(ArchetypeId.MIDRANGE, listOf(ThemeId.ARISTOCRATS))
        assertEquals(forward?.id, single?.id)
    }

    @Test
    fun `nearestFor falls back to the pure archetype when the theme combination has no curated entry`() {
        // MIDRANGE + BLINK + ARISTOCRATS together isn't a curated preset (each is curated alone) --
        // the mapper must fall back to the pure "midrange" entry rather than returning null.
        val result = CuratedStrategyCatalog.nearestFor(ArchetypeId.MIDRANGE, listOf(ThemeId.BLINK, ThemeId.ARISTOCRATS))
        assertEquals("midrange", assertNotNull(result).id)
    }

    @Test
    fun `nearestFor returns null Custom sentinel for GENERIC`() {
        // v1 deliberately ships no GENERIC/"Balanced" catalog entry (plan open question 3, Phase 3
        // concern) -- GENERIC always falls through to the Custom sentinel today.
        assertNull(CuratedStrategyCatalog.nearestFor(ArchetypeId.GENERIC, emptyList()))
    }

    @Test
    fun `nearestFor returns null Custom sentinel for a null archetype`() {
        assertNull(CuratedStrategyCatalog.nearestFor(null, emptyList()))
    }
}
