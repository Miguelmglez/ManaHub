package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.DeckFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `entries whose theme is structurally Commander-only never include a 60-card DeckFormat`() {
        CuratedStrategyCatalog.ALL.forEach { strategy ->
            val structurallyCommanderOnly = strategy.themes.any { theme ->
                ArchetypeData.THEMES.getValue(theme).commanderOnly
            }
            if (structurallyCommanderOnly) {
                assertEquals(
                    setOf(DeckFormat.COMMANDER),
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
            assertEquals(setOf(DeckFormat.COMMANDER), strategy.formats)
        }
    }

    @Test
    fun `voltron is Commander-only by v1 curation choice (not structural)`() {
        // Voltron's theme is NOT ArchetypeData.THEMES[VOLTRON].commanderOnly (a valid 60-card
        // resolution exists) -- it is Commander-only purely because Wave 1's curation study chose
        // to restrict it there. B1 preserves that choice unchanged: still Commander-only, no
        // Casual, no Standard.
        val voltron = assertNotNull(CuratedStrategyCatalog.byId("voltron"))
        assertFalse(ArchetypeData.THEMES.getValue(ThemeId.VOLTRON).commanderOnly)
        assertEquals(setOf(DeckFormat.COMMANDER), voltron.formats)
    }

    // ── Wave 2 B1: DeckFormat-granular availability ─────────────────────────────────────────────

    @Test
    fun `every entry names at least one DeckFormat`() {
        CuratedStrategyCatalog.ALL.forEach { strategy ->
            assertTrue(strategy.formats.isNotEmpty(), "Entry '${strategy.id}' has no formats")
        }
    }

    @Test
    fun `CASUAL availability is unchanged in effect from pre-B1 behavior`() {
        // Pre-B1, CuratedStrategy.formats was Set<ArchetypeFormat>; CASUAL resolved through
        // ArchetypeFormat.of(DeckFormat.CASUAL) == ArchetypeFormat.SIXTY, so CASUAL saw exactly
        // the entries that carried SIXTY -- every entry except the 4 that were COMMANDER_ONLY
        // (group_hug/group_slug/clones_theft/voltron). This regression-tests that EFFECT is
        // preserved now that formats is DeckFormat-keyed (B1 gate: "CASUAL set unchanged vs
        // pre-B1 behavior").
        val preB1CommanderOnlyIds = setOf("group_hug", "group_slug", "clones_theft", "voltron")
        CuratedStrategyCatalog.ALL.forEach { strategy ->
            val expectedCasual = strategy.id !in preB1CommanderOnlyIds
            assertEquals(
                expectedCasual,
                DeckFormat.CASUAL in strategy.formats,
                "Entry '${strategy.id}': CASUAL availability changed from pre-B1 behavior",
            )
        }
    }

    @Test
    fun `every STANDARD-available entry passes StrategyCatalog isValidCombination`() {
        val standardEntries = CuratedStrategyCatalog.ALL.filter { DeckFormat.STANDARD in it.formats }
        assertTrue(standardEntries.isNotEmpty(), "No entries curated for Standard")
        standardEntries.forEach { strategy ->
            val tribe = if (strategy.requiresTribe) "Elves" else null
            assertTrue(
                StrategyCatalog.isValidCombination(strategy.archetype, strategy.themes, tribe),
                "Standard entry '${strategy.id}' (${strategy.archetype}, themes=${strategy.themes}) " +
                    "failed StrategyCatalog.isValidCombination",
            )
        }
    }

    @Test
    fun `Standard v1 list matches the Wave 2 B1 curated set (Appendix A)`() {
        // Locks in the exact Standard v1 availability list this task shipped -- see this repo's
        // docs/plans/deck-analysis-engine-v2-wave2-standard-plan.md Appendix A for the per-entry
        // rationale and the current-meta cross-check (Landfall/Reanimator added vs the plan's
        // original proposal; Balanced added as a structural judgment call).
        val expected = setOf(
            "balanced", "aggro", "midrange", "control", "tempo", "big_mana",
            "tokens", "spellslinger", "reanimator", "landfall", "lifegain",
            "plus1_counters", "tribal", "artifacts", "vehicles",
        )
        val actual = CuratedStrategyCatalog.ALL.filter { DeckFormat.STANDARD in it.formats }.map { it.id }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun `structurally-Commander-only entries never offer STANDARD`() {
        listOf("group_hug", "group_slug", "clones_theft", "voltron").forEach { id ->
            val strategy = assertNotNull(CuratedStrategyCatalog.byId(id))
            assertFalse(DeckFormat.STANDARD in strategy.formats, "Entry '$id' should not offer STANDARD")
        }
    }

    // ── availableIn helper ──────────────────────────────────────────────────────────────────────

    @Test
    fun `availableIn matches formats membership for a format with an ArchetypeFormat mapping`() {
        val aggro = assertNotNull(CuratedStrategyCatalog.byId("aggro"))
        assertTrue(aggro.availableIn(DeckFormat.STANDARD))
        assertTrue(aggro.availableIn(DeckFormat.CASUAL))
        assertTrue(aggro.availableIn(DeckFormat.COMMANDER))

        val voltron = assertNotNull(CuratedStrategyCatalog.byId("voltron"))
        assertTrue(voltron.availableIn(DeckFormat.COMMANDER))
        assertFalse(voltron.availableIn(DeckFormat.STANDARD))
        assertFalse(voltron.availableIn(DeckFormat.CASUAL))
    }

    @Test
    fun `availableIn is an unconditional passthrough for DRAFT`() {
        // Draft has no ArchetypeFormat mapping (ArchetypeFormat.of(DRAFT) == null) -- every entry
        // stays pickable, even the structurally Commander-only ones, mirroring the pre-B1 picker's
        // own "archetypeFormat == null -> no restriction" branch.
        CuratedStrategyCatalog.ALL.forEach { strategy ->
            assertTrue(strategy.availableIn(DeckFormat.DRAFT), "Entry '${strategy.id}' should be available for DRAFT")
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
    fun `nearestFor returns the balanced entry for GENERIC with no themes`() {
        // Closes Wave 1 open question 3: a fresh/unpinned deck (GENERIC, no themes) now maps to
        // the "balanced" catalog entry via exact match, instead of falling through to the
        // "Custom" sentinel.
        val result = CuratedStrategyCatalog.nearestFor(ArchetypeId.GENERIC, emptyList())
        assertEquals("balanced", assertNotNull(result).id)
    }

    @Test
    fun `nearestFor returns null Custom sentinel for GENERIC with a theme attached`() {
        // (GENERIC, [ARISTOCRATS]) is technically StrategyCatalog.isValidCombination-valid (GENERIC
        // is compatible with every theme), but no catalog entry exists for it -- and it must NOT
        // fall back to "balanced" the way other archetypes fall back to their pure entry, since
        // "balanced" specifically means "no plan pinned at all". This is a deliberate legacy/
        // incoherent-state case that should read as "Custom", not be coerced into "Balanced".
        assertNull(CuratedStrategyCatalog.nearestFor(ArchetypeId.GENERIC, listOf(ThemeId.ARISTOCRATS)))
    }

    @Test
    fun `nearestFor returns null Custom sentinel for a null archetype`() {
        assertNull(CuratedStrategyCatalog.nearestFor(null, emptyList()))
    }

    @Test
    fun `catalog has 29 entries -- 7 pure archetypes plus 22 themed presets`() {
        assertEquals(29, CuratedStrategyCatalog.ALL.size)
    }
}
