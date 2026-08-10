package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.DeckFormat

// ═══════════════════════════════════════════════════════════════════════════════
//  LandTargetResolver — Deck Wizard & Engine Rework plan, Workstream 6 ("One land
//  engine").
//
//  Root cause (plan §0 F2): the wizard (BuildDeckFromTemplateUseCase.computeLandTarget,
//  now REMOVED) and Deck Studio (DeckStudioViewModel.calculateLandDeltas) used to
//  compute "how many lands should this deck have" through two entirely different
//  code paths — the wizard via an archetype-skeleton-or-dynamicLandIdeal split, Studio
//  via BasicLandCalculator's convenience overload that hardcodes `format
//  .targetLandCount` (archetype-blind, dynamicLandIdeal-blind). This object is the
//  SINGLE shared entry point both now call, so a wizard-built deck reopened in Studio
//  computes an IDENTICAL land target and shows zero land deltas.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * The one authoritative "how many lands should this deck have" computation, shared by
 * [com.mmg.manahub.feature.decks.domain.template.BuildDeckFromTemplateUseCase] (the wizard) and
 * [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel] (Studio's basic-land suggestion).
 *
 * A stateless object mirroring the sibling [ArchetypeSkeletonResolver]/[DeckSkeletons] objects in
 * this same package — no DI needed since [ManaBaseAnalyzer] is a zero-arg-constructible pure class.
 */
object LandTargetResolver {

    /**
     * Resolves the target land count for a deck.
     *
     * Behavior split (preserved from the pre-unification wizard logic):
     * - **60-card constructed formats**: [ManaBaseAnalyzer.dynamicLandIdeal] over [profile]
     *   (relax-only, clamped to the GENERIC per-format skeleton's own land band — see that
     *   function's KDoc for why it never reads the archetype skeleton), falling back to
     *   [archetypeSkeleton]'s own land ideal, then the generic per-format skeleton's ideal.
     * - **Commander / other non-60-card formats**: [archetypeSkeleton]'s land ideal (color-modulated
     *   by [ArchetypeSkeletonResolver.resolveWithColor]), falling back to the generic per-format
     *   skeleton's ideal.
     *
     * **Deliberate behavior change vs. the pre-WS6 wizard** (documented trade-off, plan §WS6):
     * for a COMMUNITY-sourced Commander template, the wizard used to fall back to the real EDHREC
     * aggregate's own average land count (`DeckTemplate.landTarget`) when `dynamicLandIdeal`/the
     * skeleton ideal was unavailable. That fallback is GONE — the actual land count filled for a
     * Commander build now ALWAYS comes from [archetypeSkeleton], never a community aggregate's own
     * average. This is intentional: Studio has no access to community aggregate data at
     * suggestion-time (it only has the deck's persisted `archetypeOverride`/`themesOverride`), so
     * unifying both call sites on the archetype skeleton is the only way they can agree byte-for-byte.
     *
     * @param archetypeSkeleton the resolved, color-modulated skeleton, or `null` for the
     *   GENERIC-with-no-themes "no skeleton" case (same convention as
     *   `BuildDeckFromTemplateUseCase.resolveArchetypeSkeleton` /
     *   `DeckDoctorOrchestrator.resolveArchetypeSkeleton`).
     * @param profile a [DeckProfile] built from the mainboard whose land target is being decided
     *   (nonland-only entries recommended — see call-site notes in `BuildDeckFromTemplateUseCase`/
     *   `DeckStudioViewModel`); required for the 60-card branch ([ManaBaseAnalyzer.dynamicLandIdeal]
     *   reads its `roleCounts`/`avgCmc`/`nonLandCount`), ignored for Commander. `null` is safe (falls
     *   back to the skeleton/format default) for a caller that doesn't have a mainboard yet.
     */
    fun resolve(
        format: DeckFormat,
        archetypeSkeleton: ResolvedArchetypeSkeleton?,
        profile: DeckProfile?,
        manaBaseAnalyzer: ManaBaseAnalyzer = ManaBaseAnalyzer(),
    ): Int {
        val genericFallback = DeckSkeletons.forFormat(format).idealFor(DeckRole.LAND)
        return if (format.isSixtyCardConstructed) {
            val dynamic = profile?.let { manaBaseAnalyzer.dynamicLandIdeal(it) } ?: 0
            dynamic.takeIf { it > 0 }
                ?: archetypeSkeleton?.lands?.ideal?.takeIf { it > 0 }
                ?: genericFallback
        } else {
            archetypeSkeleton?.lands?.ideal?.takeIf { it > 0 } ?: genericFallback
        }
    }

    /**
     * The valid `[min, max]` band for clamping a derived land count — same skeleton source as
     * [resolve]'s ideal, falling back to the generic per-format skeleton's own LAND role bounds when
     * no archetype skeleton is resolved.
     */
    fun band(format: DeckFormat, archetypeSkeleton: ResolvedArchetypeSkeleton?): IntRange {
        archetypeSkeleton?.lands?.let { return it.min..it.max }
        val slot = DeckSkeletons.forFormat(format).slots.firstOrNull { it.role == DeckRole.LAND }
        return (slot?.min ?: 0)..(slot?.max ?: Int.MAX_VALUE)
    }
}
