package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.DeckIdentitySeedTags
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.engine.TribeDeriver

/** One tribe candidate the STRATEGY step's "Tribe" axis can offer, derived from a commander's own
 * signals -- a plain commonMain-compatible shape (the wizard UI maps this onto
 * [com.mmg.manahub.feature.decks.presentation.components.TribeOption] at the call site; this file
 * cannot import that Compose/`:app` type per the KMP layering rule). */
data class DerivedTribeCandidate(val key: String, val label: String)

/** The STRATEGY step's derived, commander-specific candidate list (plan §2.2) -- ranked, capped,
 * source-1-first. [archetypes]/[themes] are rendered through the shared `StrategyPickerSheet`'s
 * `availableArchetypes`/`availableThemes` filter (the picker itself always additionally offers
 * [ArchetypeId.GENERIC] as the Commander-only "Balanced" escape hatch, per D-B/plan 2.2 -- that
 * union happens at the picker call site, not here). */
data class DerivedCommanderStrategies(
    val archetypes: List<ArchetypeId>,
    val themes: List<ThemeId>,
    val tribes: List<DerivedTribeCandidate>,
) {
    companion object {
        val EMPTY = DerivedCommanderStrategies(archetypes = emptyList(), themes = emptyList(), tribes = emptyList())
    }
}

/**
 * Deck Wizard & Engine Rework plan (`docs/plans/deck-wizard-rework-plan.md`), Workstream 2.2 --
 * once a commander is picked, derive the SHORT list of strategies it actually supports, from 3
 * ranked sources (union, source-1 first, capped):
 *
 * 1. **The commander's own `card_strategy_tags` payload.** Per the current-state doc §6, the READ
 *    path ([com.mmg.manahub.core.domain.repository.CardStrategyTagsRepository.getStrategyTags])
 *    only exposes already-dictionary-resolved [CardTag]s (+ bare tribe words) — it does NOT expose
 *    raw `themes`/`archetypes` synergy-weight maps on the read side (those only exist on the WRITE/
 *    submission DTO). [ownTags]/[ownTribes] are that `Found` result's fields; this use case maps
 *    [ownTags] onto the taxonomy via the SAME single bridge every other call site in this codebase
 *    uses ([DeckIdentitySeedTags.archetypeForTag]/`.themeForTag` — mirrors
 *    `CollectionLeanSection`/`DeckWizardViewModel.onSelectDirectionTag`'s exact resolution), rather
 *    than inventing a second mapping over a shape the repository doesn't actually surface. This is
 *    a deliberate, documented adaptation of the plan's literal "themes/archetypes payload" wording —
 *    see `feedback_ws2_commander_strategy_derivation` memory for the full rationale.
 * 2. **The EDHREC commander aggregate's `themeTags`** ([edhrecThemeNames], the existing
 *    `DeckWizardViewModel.loadThemeTags` fetch) mapped via [ThemeId.fromDisplayName]. Contributes
 *    no archetype signal (EDHREC's per-commander aggregate names themes, not macro archetypes).
 * 3. **Fallback, ONLY when sources 1+2 both resolve to zero candidates** — [fallbackTags] (the
 *    commander `Card`'s own already-persisted `tags`/`userTags`, i.e. whatever on-device/pipeline
 *    resolution already ran at cache time) mapped the same way, plus [fallbackTribeKeys] (the
 *    commander's [TribeDeriver] payoff/subtype tribes — "an Elf-lord commander ⇒ TRIBAL/Elves
 *    offered", per the plan).
 *
 * Tribe candidates ([DerivedTribeCandidates.tribes]) are gathered independently of the
 * archetype/theme cap (a secondary axis, only relevant once [ThemeId.TRIBAL] is picked).
 */
class DeriveCommanderStrategiesUseCase {

    operator fun invoke(
        ownTags: List<CardTag> = emptyList(),
        ownTribes: List<String> = emptyList(),
        edhrecThemeNames: List<String> = emptyList(),
        fallbackTags: List<CardTag> = emptyList(),
        fallbackTribeKeys: Set<String> = emptySet(),
    ): DerivedCommanderStrategies {
        val source1Archetypes = ownTags.mapNotNull { DeckIdentitySeedTags.archetypeForTag(it) }.distinct()
        val source1Themes = ownTags.mapNotNull { DeckIdentitySeedTags.themeForTag(it) }.distinct()
        val source1Tribes = ownTribes.map { word -> DerivedTribeCandidate(TribeDeriver.TRIBE_PREFIX + word, displayLabel(word)) }

        val source2Themes = edhrecThemeNames.mapNotNull { ThemeId.fromDisplayName(it) }.distinct()

        var archetypes = source1Archetypes
        var themes = (source1Themes + source2Themes).distinct()
        var tribes = source1Tribes.distinctBy { it.key }

        if (archetypes.isEmpty() && themes.isEmpty()) {
            // Source 3 -- only consulted when sources 1+2 both came back empty (a rare path given
            // card_strategy_tags coverage is documented as complete for essentially every real card).
            archetypes = fallbackTags.mapNotNull { DeckIdentitySeedTags.archetypeForTag(it) }.distinct()
            themes = fallbackTags.mapNotNull { DeckIdentitySeedTags.themeForTag(it) }.distinct()
            tribes = fallbackTribeKeys
                .map { key -> DerivedTribeCandidate(key, displayLabel(key.removePrefix(TribeDeriver.TRIBE_PREFIX))) }
                .distinctBy { it.key }
        }

        // Cap the combined archetype+theme candidate count (plan: "cap the list (~6-8)").
        // Archetypes-first is a deliberate, stable tie-break: in the normal (non-fallback) path every
        // archetype candidate is ALREADY source-1 (EDHREC's per-commander aggregate names only
        // themes, never archetypes -- see this class's KDoc point 2), so archetypes never need to
        // compete with a lower-ranked theme for cap budget; this ordering only matters in the rare
        // fallback path, where it is an arbitrary but deterministic choice.
        val cappedArchetypes = archetypes.take(MAX_CANDIDATES)
        val remainingBudget = (MAX_CANDIDATES - cappedArchetypes.size).coerceAtLeast(0)
        val cappedThemes = themes.take(remainingBudget)

        return DerivedCommanderStrategies(
            archetypes = cappedArchetypes,
            themes = cappedThemes,
            tribes = tribes.take(MAX_TRIBES),
        )
    }

    /** Best-effort capitalization for a bare tribe word ("elf" -> "Elf"). Deliberately NOT
     * pluralized (unlike [com.mmg.manahub.feature.decks.domain.template.CollectionProfileUseCase]'s
     * `pluralize` helper) -- a commander's OWN payoff/subtype tribe is a single-tribe signal shown
     * next to its identity pick, not a collection-wide copy count where pluralization reads
     * naturally; kept simple and honest about being a display label, not a grammatical claim. */
    private fun displayLabel(word: String): String = word.replaceFirstChar { it.uppercase() }

    private companion object {
        const val MAX_CANDIDATES = 8
        const val MAX_TRIBES = 8
    }
}
