package com.mmg.manahub.feature.decks.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deck Wizard & Engine Rework plan (`docs/plans/deck-wizard-rework-plan.md`), Workstream 1.1 --
 * [StrategyCatalog]'s data-shape invariants: every archetype/theme has real player-facing copy,
 * the compatibility matrix is internally consistent (single-source-of-truth inverse view), and
 * [StrategyCatalog.isValidCombination] enforces the documented rules. Content (which archetypes a
 * given theme lists) is curated editorial judgment, not asserted line-by-line here — these tests
 * guard the SHAPE of the data, mirroring [ColorStrategyAffinityTest]'s own "shape, not content"
 * scoping note.
 */
class StrategyCatalogTest {

    private val minDescriptionLength = 40

    @Test
    fun `every archetype has a real, non-trivial description`() {
        ArchetypeId.entries.forEach { archetype ->
            val description = StrategyCatalog.description(archetype)
            assertTrue(description.isNotBlank(), "$archetype should have a description")
            assertTrue(
                description.length > minDescriptionLength,
                "$archetype description should be a real sentence, was: \"$description\"",
            )
        }
    }

    @Test
    fun `every theme has a real, non-trivial description`() {
        ThemeId.entries.forEach { theme ->
            val description = StrategyCatalog.description(theme)
            assertTrue(description.isNotBlank(), "$theme should have a description")
            assertTrue(
                description.length > minDescriptionLength,
                "$theme description should be a real sentence, was: \"$description\"",
            )
        }
    }

    @Test
    fun `every theme has at least one compatible archetype`() {
        ThemeId.entries.forEach { theme ->
            assertTrue(
                StrategyCatalog.compatibleArchetypes(theme).isNotEmpty(),
                "$theme should list at least one compatible archetype",
            )
        }
    }

    @Test
    fun `no theme lists GENERIC among its own compatible archetypes`() {
        // GENERIC is compatible with everything by construction (see isValidCombination) and is
        // deliberately never enumerated inside a theme's own curated set -- a theme listing it
        // explicitly would be redundant and could silently drift from that construction rule.
        ThemeId.entries.forEach { theme ->
            assertFalse(ArchetypeId.GENERIC in StrategyCatalog.compatibleArchetypes(theme), "$theme should not list GENERIC")
        }
    }

    @Test
    fun `only TRIBAL requires a tribe`() {
        ThemeId.entries.forEach { theme ->
            val expected = theme == ThemeId.TRIBAL
            assertEquals(expected, StrategyCatalog.requiresTribe(theme), "$theme requiresTribe should be $expected")
        }
    }

    @Test
    fun `every theme's default archetype is one of its own compatible archetypes`() {
        ThemeId.entries.forEach { theme ->
            val compatible = StrategyCatalog.compatibleArchetypes(theme)
            assertTrue(
                StrategyCatalog.defaultArchetype(theme) in compatible,
                "$theme's defaultArchetype should be a member of its own compatibleArchetypes",
            )
        }
    }

    @Test
    fun `compatibleThemes is the exact inverse of compatibleArchetypes -- single source of truth`() {
        // Derived-from-the-same-table invariant (WS 1.1 "generate the inverse, never hand-maintain
        // two directions"): for every (archetype, theme) pair, archetype in theme's set iff theme
        // in archetype's set.
        ArchetypeId.entries.filter { it != ArchetypeId.GENERIC }.forEach { archetype ->
            val fromArchetype = StrategyCatalog.compatibleThemes(archetype)
            ThemeId.entries.forEach { theme ->
                val fromTheme = archetype in StrategyCatalog.compatibleArchetypes(theme)
                assertEquals(
                    fromTheme,
                    theme in fromArchetype,
                    "$archetype <-> $theme compatibility should agree in both directions",
                )
            }
        }
    }

    @Test
    fun `GENERIC is compatible with every theme via compatibleThemes`() {
        assertEquals(ThemeId.entries.toSet(), StrategyCatalog.compatibleThemes(ArchetypeId.GENERIC))
    }

    // ── isValidCombination ──────────────────────────────────────────────────────────────────────

    @Test
    fun `GENERIC (or null) archetype is valid with any theme combination`() {
        assertTrue(StrategyCatalog.isValidCombination(null, listOf(ThemeId.STAX)))
        assertTrue(StrategyCatalog.isValidCombination(ArchetypeId.GENERIC, listOf(ThemeId.STAX, ThemeId.WHEELS)))
    }

    @Test
    fun `a theme incompatible with the picked archetype is invalid`() {
        // STAX explicitly excludes AGGRO (plan's own worked example).
        assertFalse(StrategyCatalog.isValidCombination(ArchetypeId.AGGRO, listOf(ThemeId.STAX)))
    }

    @Test
    fun `a theme compatible with the picked archetype is valid`() {
        assertTrue(StrategyCatalog.isValidCombination(ArchetypeId.CONTROL, listOf(ThemeId.STAX)))
    }

    @Test
    fun `more than MAX_THEMES is always invalid`() {
        val themes = listOf(ThemeId.LIFEGAIN, ThemeId.TOKENS, ThemeId.ARISTOCRATS)
        assertTrue(themes.size > StrategyCatalog.MAX_THEMES)
        assertFalse(StrategyCatalog.isValidCombination(ArchetypeId.AGGRO, themes))
    }

    @Test
    fun `TRIBAL without a tribe is invalid`() {
        assertFalse(StrategyCatalog.isValidCombination(ArchetypeId.MIDRANGE, listOf(ThemeId.TRIBAL), tribe = null))
        assertFalse(StrategyCatalog.isValidCombination(ArchetypeId.MIDRANGE, listOf(ThemeId.TRIBAL), tribe = "   "))
    }

    @Test
    fun `TRIBAL with a tribe is valid when the archetype is compatible`() {
        assertTrue(StrategyCatalog.isValidCombination(ArchetypeId.MIDRANGE, listOf(ThemeId.TRIBAL), tribe = "tribe:elf"))
    }

    @Test
    fun `a non-blank tribe with no TRIBAL theme is invalid -- tribe is only meaningful with TRIBAL`() {
        assertFalse(StrategyCatalog.isValidCombination(ArchetypeId.MIDRANGE, listOf(ThemeId.TOKENS), tribe = "tribe:elf"))
    }

    @Test
    fun `an empty theme list is always valid regardless of archetype`() {
        ArchetypeId.entries.forEach { archetype ->
            assertTrue(StrategyCatalog.isValidCombination(archetype, emptyList()))
        }
    }
}
