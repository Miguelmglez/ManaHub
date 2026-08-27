package com.mmg.manahub.feature.decks.domain.engine

// ═══════════════════════════════════════════════════════════════════════════════
//  StrategyPickerState — Deck Wizard & Engine Rework plan, Workstream 1.2 -- the pure selection
//  logic behind the shared picker component (`StrategyPickerSheet`, `:app` presentation layer).
//  Kept in commonMain and Compose-free so it is independently unit-testable (the codebase has no
//  Compose UI test harness -- see this workstream's own test notes) and so the SAME transition
//  rules apply wherever the picker is mounted (wizard Direction/strategy steps WS 2/3, the
//  Doctor's `ArchetypePlanSheet` WS 8 -- neither wired in this pass).
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * The picker's hoisted selection state -- three axes (WS 1.2): [archetype] (Game plan,
 * single-select), [themes] (Strategy, up to [StrategyCatalog.MAX_THEMES]), [tribe] (only
 * meaningful alongside [ThemeId.TRIBAL], see [StrategyProfile]'s own contract).
 */
data class StrategyPickerSelection(
    val archetype: ArchetypeId? = null,
    val themes: List<ThemeId> = emptyList(),
    val tribe: String? = null,
) {
    /** True when the current [themes] pick requires a tribe (i.e. [ThemeId.TRIBAL] is selected) --
     * drives whether the picker's third axis ("Tribe") renders at all. */
    val requiresTribe: Boolean get() = ThemeId.TRIBAL in themes

    /** True when this selection is fully valid per [StrategyCatalog.isValidCombination] -- e.g. the
     * "Next"/"Confirm" action in a hosting screen should gate on this. */
    val isValid: Boolean get() = StrategyCatalog.isValidCombination(archetype, themes, tribe)

    /** Resolves this selection into a [StrategyProfile], filling in [colors] from the caller (the
     * picker itself never owns color state -- mirrors [ColorStrategyEntry.toStrategyProfile]'s own
     * "colors filled in by the caller" convention). */
    fun toProfile(colors: Set<ManaColor> = emptySet()): StrategyProfile =
        StrategyProfile(archetype = archetype, themes = themes, tribe = tribe, colors = colors)

    companion object {
        val EMPTY = StrategyPickerSelection()
    }
}

/**
 * Pure state-transition functions for [StrategyPickerSelection] -- every UI action (archetype tap,
 * theme tap, tribe tap) goes through exactly one of these so the transition rules can never drift
 * between the wizard's and the Doctor's eventual call sites.
 */
object StrategyPickerLogic {

    /**
     * Picking a new [archetype] (or clearing it, `null`) drops any already-selected theme that is
     * no longer compatible (never leaves the selection in an invalid state per
     * [StrategyCatalog.isValidCombination]) -- and drops [StrategyPickerSelection.tribe] too when
     * that theme was [ThemeId.TRIBAL] (the tribe pick has no meaning without it, see
     * [StrategyProfile]'s own contract).
     */
    fun selectArchetype(current: StrategyPickerSelection, archetype: ArchetypeId?): StrategyPickerSelection {
        // Deck Analysis Engine v3: ArchetypeId.GENERIC no longer exists -- `null` is the sole
        // "unpinned, compatible with every theme" state now.
        val survivingThemes = if (archetype == null) {
            current.themes
        } else {
            current.themes.filter { theme -> archetype in StrategyCatalog.compatibleArchetypes(theme) }
        }
        val survivingTribe = if (ThemeId.TRIBAL in survivingThemes) current.tribe else null
        return current.copy(archetype = archetype, themes = survivingThemes, tribe = survivingTribe)
    }

    /**
     * Toggles [theme] in/out of the current selection. Adding is a no-op when the theme is
     * INCOMPATIBLE with the current archetype (the picker renders incompatible items disabled per
     * WS 1.2 -- this is the defensive backstop for a caller that ignores that) or when
     * [StrategyCatalog.MAX_THEMES] is already reached. Removing [ThemeId.TRIBAL] clears
     * [StrategyPickerSelection.tribe] alongside it.
     */
    fun toggleTheme(current: StrategyPickerSelection, theme: ThemeId): StrategyPickerSelection {
        val alreadySelected = theme in current.themes
        val newThemes = when {
            alreadySelected -> current.themes - theme
            current.themes.size >= StrategyCatalog.MAX_THEMES -> return current
            !isThemeSelectable(current, theme) -> return current
            else -> current.themes + theme
        }
        val newTribe = if (ThemeId.TRIBAL in newThemes) current.tribe else null
        return current.copy(themes = newThemes, tribe = newTribe)
    }

    /** Picks (or clears, `null`/blank) the tribe axis. No-op validation here -- the picker only
     * renders this axis when [StrategyPickerSelection.requiresTribe] is true, and a caller passing
     * a tribe with no [ThemeId.TRIBAL] selected would simply produce a selection whose [isValid]
     * (via [StrategyCatalog.isValidCombination]) is false, surfacing the mistake rather than
     * hiding it. */
    fun selectTribe(current: StrategyPickerSelection, tribe: String?): StrategyPickerSelection =
        current.copy(tribe = tribe?.takeIf { it.isNotBlank() })

    /** Whether [theme] can be added to [selection] right now -- `null` (no archetype pin --
     * Deck Analysis Engine v3 removed `ArchetypeId.GENERIC`) accepts every theme (the unpinned
     * state restricts nothing); any real archetype requires [theme] to list it in
     * [StrategyCatalog.compatibleArchetypes]. Drives the picker's "incompatible options render
     * disabled" requirement (WS 1.2) -- never hidden, so this is a pure predicate the UI can query
     * per-item without mutating state. */
    fun isThemeSelectable(selection: StrategyPickerSelection, theme: ThemeId): Boolean {
        val archetype = selection.archetype ?: return true
        return archetype in StrategyCatalog.compatibleArchetypes(theme)
    }
}
