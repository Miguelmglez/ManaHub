package com.mmg.manahub.feature.decks.domain.engine

/**
 * Deck Engine Unification plan (`docs/plans/deck-engine-unification-plan.md`, D2) -- the SINGLE
 * strategy taxonomy for the whole app. The wizard's Direction/Identity picks, the Deck Doctor's
 * archetype/theme evaluation, and the Discoveries/Synergy browser's cluster hand-off all speak this
 * vocabulary now.
 *
 * Replaces [DeckWizardSpec]'s old `strategyHint`/`tagHint`/`themeHint`/`tribeHint` quartet
 * (RC1 -- "vocabulary fragmentation"). [DeckIdentitySeedTags] remains the ONLY profile -> [com.mmg
 * .manahub.core.model.CardTag] seed-tag bridge; this type never carries tags itself.
 *
 * @param archetype the macro [ArchetypeId] pin. `null` = unpinned (the build/evaluation resolves
 *   against the bare generic baseline skeleton -- Deck Analysis Engine v3 removed
 *   `ArchetypeId.GENERIC`, so `null` is now the sole "no macro pin" representation).
 * @param themes at most 2 [ThemeId] picks (mirrors [ArchetypeSkeletonResolver]'s own 2-theme cap).
 *   In the wizard, index 0 is the Direction step's pick, index 1 (when present) is the Identity
 *   step's own EDHREC theme picker -- both slots exist independently, same as the pre-unification
 *   quartet's `strategyHint`/`tagHint` (Direction) + `themeHint` (Identity).
 * @param tribe a raw runtime `tribe:<subtype>` key ([TribeDeriver.TRIBE_PREFIX]-prefixed), or
 *   `null`. NEVER persisted as a [com.mmg.manahub.core.model.CardTag] -- mirrors [DeckScorer]'s own
 *   tribe-key contract (runtime-derived only, see [TribeDeriver]).
 * @param colors the color identity/filter this profile targets. In the wizard build path this
 *   mirrors [DeckWizardSpec.colorIdentity] (which stays the authoritative field every build-engine
 *   call site reads -- this is metadata/echo in RUN 1, becoming load-bearing for the colors-first
 *   Flow B (plan Phase 3, not yet implemented)).
 */
data class StrategyProfile(
    val archetype: ArchetypeId? = null,
    val themes: List<ThemeId> = emptyList(),
    val tribe: String? = null,
    val colors: Set<ManaColor> = emptySet(),
) {
    companion object {
        val EMPTY = StrategyProfile()
    }
}
