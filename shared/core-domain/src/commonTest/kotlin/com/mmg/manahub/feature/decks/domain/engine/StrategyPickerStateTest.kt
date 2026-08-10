package com.mmg.manahub.feature.decks.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deck Wizard & Engine Rework plan (`docs/plans/deck-wizard-rework-plan.md`), Workstream 1.2 --
 * [StrategyPickerLogic]'s pure state-transition rules for the shared picker component. Compose-free
 * so it is testable without a UI harness (the codebase has no Compose UI test infrastructure).
 */
class StrategyPickerStateTest {

    @Test
    fun `selecting an archetype drops themes that are no longer compatible`() {
        // STAX (CONTROL/COMBO only) is incompatible with AGGRO -- picking AGGRO after STAX was
        // selected must drop it rather than leaving an invalid selection.
        val withStax = StrategyPickerSelection(archetype = ArchetypeId.CONTROL, themes = listOf(ThemeId.STAX))
        val result = StrategyPickerLogic.selectArchetype(withStax, ArchetypeId.AGGRO)

        assertEquals(ArchetypeId.AGGRO, result.archetype)
        assertTrue(ThemeId.STAX !in result.themes)
        assertTrue(result.isValid)
    }

    @Test
    fun `selecting an archetype keeps themes that remain compatible`() {
        val withAristocrats = StrategyPickerSelection(archetype = ArchetypeId.MIDRANGE, themes = listOf(ThemeId.ARISTOCRATS))
        // ARISTOCRATS is compatible with both MIDRANGE and AGGRO -- switching should keep it.
        val result = StrategyPickerLogic.selectArchetype(withAristocrats, ArchetypeId.AGGRO)

        assertEquals(listOf(ThemeId.ARISTOCRATS), result.themes)
    }

    @Test
    fun `selecting GENERIC never drops any already-selected theme`() {
        val selection = StrategyPickerSelection(archetype = ArchetypeId.CONTROL, themes = listOf(ThemeId.STAX))
        val result = StrategyPickerLogic.selectArchetype(selection, ArchetypeId.GENERIC)

        assertEquals(listOf(ThemeId.STAX), result.themes)
    }

    @Test
    fun `switching away from an archetype that required TRIBAL's tribe clears the tribe when TRIBAL is dropped`() {
        val selection = StrategyPickerSelection(
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.TRIBAL),
            tribe = "tribe:elf",
        )
        // COMBO is not in TRIBAL's compatible set -- TRIBAL (and its tribe) must be dropped.
        val result = StrategyPickerLogic.selectArchetype(selection, ArchetypeId.COMBO)

        assertTrue(ThemeId.TRIBAL !in result.themes)
        assertNull(result.tribe)
    }

    @Test
    fun `toggling an unselected compatible theme adds it`() {
        val selection = StrategyPickerSelection(archetype = ArchetypeId.CONTROL)
        val result = StrategyPickerLogic.toggleTheme(selection, ThemeId.STAX)

        assertEquals(listOf(ThemeId.STAX), result.themes)
    }

    @Test
    fun `toggling an already-selected theme removes it`() {
        val selection = StrategyPickerSelection(archetype = ArchetypeId.CONTROL, themes = listOf(ThemeId.STAX))
        val result = StrategyPickerLogic.toggleTheme(selection, ThemeId.STAX)

        assertTrue(result.themes.isEmpty())
    }

    @Test
    fun `toggling an incompatible theme is a no-op`() {
        val selection = StrategyPickerSelection(archetype = ArchetypeId.AGGRO)
        val result = StrategyPickerLogic.toggleTheme(selection, ThemeId.STAX)

        assertTrue(result.themes.isEmpty())
        assertEquals(selection, result)
    }

    @Test
    fun `toggling a 3rd theme past MAX_THEMES is a no-op`() {
        val selection = StrategyPickerSelection(
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.LIFEGAIN, ThemeId.ARISTOCRATS),
        )
        assertEquals(StrategyCatalog.MAX_THEMES, selection.themes.size)

        val result = StrategyPickerLogic.toggleTheme(selection, ThemeId.TOKENS)

        assertEquals(selection, result)
    }

    @Test
    fun `removing TRIBAL via toggle clears the tribe`() {
        val selection = StrategyPickerSelection(
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.TRIBAL),
            tribe = "tribe:goblin",
        )
        val result = StrategyPickerLogic.toggleTheme(selection, ThemeId.TRIBAL)

        assertTrue(result.themes.isEmpty())
        assertNull(result.tribe)
    }

    @Test
    fun `selecting TRIBAL flags requiresTribe`() {
        val selection = StrategyPickerSelection(archetype = ArchetypeId.MIDRANGE)
        val result = StrategyPickerLogic.toggleTheme(selection, ThemeId.TRIBAL)

        assertTrue(result.requiresTribe)
        assertFalse(result.isValid) // no tribe picked yet
    }

    @Test
    fun `picking a tribe after TRIBAL completes a valid selection`() {
        val withTribal = StrategyPickerLogic.toggleTheme(
            StrategyPickerSelection(archetype = ArchetypeId.MIDRANGE),
            ThemeId.TRIBAL,
        )
        val result = StrategyPickerLogic.selectTribe(withTribal, "tribe:elf")

        assertEquals("tribe:elf", result.tribe)
        assertTrue(result.isValid)
    }

    @Test
    fun `selecting a blank tribe clears it`() {
        val selection = StrategyPickerSelection(
            archetype = ArchetypeId.MIDRANGE,
            themes = listOf(ThemeId.TRIBAL),
            tribe = "tribe:elf",
        )
        val result = StrategyPickerLogic.selectTribe(selection, "   ")
        assertNull(result.tribe)
    }

    @Test
    fun `isThemeSelectable is permissive under GENERIC or null archetype`() {
        assertTrue(StrategyPickerLogic.isThemeSelectable(StrategyPickerSelection(archetype = null), ThemeId.STAX))
        assertTrue(StrategyPickerLogic.isThemeSelectable(StrategyPickerSelection(archetype = ArchetypeId.GENERIC), ThemeId.STAX))
    }

    @Test
    fun `isThemeSelectable rejects an incompatible theme under a specific archetype`() {
        assertFalse(StrategyPickerLogic.isThemeSelectable(StrategyPickerSelection(archetype = ArchetypeId.AGGRO), ThemeId.STAX))
    }

    @Test
    fun `toProfile carries the caller-supplied colors through unchanged`() {
        val selection = StrategyPickerSelection(archetype = ArchetypeId.RAMP, themes = listOf(ThemeId.LANDFALL))
        val profile = selection.toProfile(setOf(ManaColor.G))

        assertEquals(ArchetypeId.RAMP, profile.archetype)
        assertEquals(listOf(ThemeId.LANDFALL), profile.themes)
        assertEquals(setOf(ManaColor.G), profile.colors)
    }
}
