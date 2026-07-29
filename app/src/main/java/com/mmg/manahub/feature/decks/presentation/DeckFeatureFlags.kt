package com.mmg.manahub.feature.decks.presentation

/**
 * Compile-time UI feature flags for the Decks feature. Flip a flag to `false` to hide the
 * corresponding UI entry point again. Hiding is UI-only — all underlying logic/ViewModels/
 * composables stay compiled.
 *
 * **2026-07-17 (Deck Builder v2 Phase 3/5 flag restructure, plan D10/§3.7):**
 * [DECK_BUILDER_V2_ENABLED] and [DISCOVERIES_V2_ENABLED] flipped ON as their respective surfaces
 * landed (wizard + generation/result screens; Discoveries v2 clustering). `DECK_STUDIO_SUGGESTIONS_TAB_ENABLED`
 * was ALSO flipped back to `true` some time after 2026-07-14 (for the Community/Archetype plan
 * Suggestions-tab work) — see `project_deck_builder_v2` memory for the exact point in that
 * campaign.
 *
 * **2026-07-28 (Deck Wizard & Engine Rework plan, WS7.2):** the LEGACY siblings of
 * [DECK_BUILDER_V2_ENABLED] and [DISCOVERIES_V2_ENABLED] (`DECK_STUDIO_BUILD_FROM_SEED_ENABLED`
 * and `DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED`, plus the `SeedsContent`/`BuildDeckFromSeedsUseCase`/
 * `DeckMagicEngine.discoverSynergies` content they gated) were RETIRED after a parity audit found
 * nothing left to port — `DeckStudioScreen.kt`'s `handleBuildFromSeed`/`seedEnabled`/
 * `inspirationsEnabled` are now plain v2-flag checks (no more "legacy OR v2" gates). See
 * `feature_deck_wizard_rework_ws7_retirement` memory for the retirement record.
 */
object DeckFeatureFlags {
    /** Deck Studio "Suggestions" tab (inline Deck Doctor). Hidden 2026-07-21 (temporary — was
     * `true` since the Community/Archetype plan Suggestions-tab launch). Flip to `true` to
     * re-enable; see `docs/hidden-features/deck-studio-suggestions.md`. */
    const val DECK_STUDIO_SUGGESTIONS_TAB_ENABLED = false

    /**
     * Deck Builder v2 (`docs/plans/deck-builder-v2-plan.md`) wizard + generation + result screens.
     * Phase 3 landed 2026-07-17: `Screen.DeckWizard` + `DeckWizardViewModel` + the 4-step wizard/
     * generation/result composables. Gates the Studio's "Build from seed" entry point (top-bar
     * overflow item + empty-state card). The legacy sibling flag
     * `DECK_STUDIO_BUILD_FROM_SEED_ENABLED` (and the `SeedsContent`/`BuildDeckFromSeedsUseCase`
     * sheet it gated) was RETIRED in the Deck Wizard & Engine Rework plan, WS7.2 (2026-07-28) —
     * this is the entry point's only flag now (the `docs/hidden-features/deck-studio-build-from-seed.md`
     * doc was deleted alongside it; see `feature_deck_wizard_rework_ws7_retirement` memory).
     */
    const val DECK_BUILDER_V2_ENABLED = false

    /**
     * Deck Builder v2 Phase 5 (plan §3.5): `DiscoverSynergiesV2UseCase` — identity-only clustering
     * (STRATEGY/ARCHETYPE tags + derived `tribe:<x>` keys), ranked by [com.mmg.manahub.feature.decks
     * .domain.engine.DeckScorer.fit], color-coherent. Gates the Studio's "Browse inspirations" entry
     * point. "Build this" hands off to the v2 wizard pre-filled with strategy/tribe + colors. The
     * legacy sibling flag `DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED` (and the ANY-tag-category
     * `DeckMagicEngine.discoverSynergies` content it gated) was RETIRED in the Deck Wizard & Engine
     * Rework plan, WS7.2 (2026-07-28) — this is the entry point's only flag now (the
     * `docs/hidden-features/deck-studio-inspirations.md` doc was deleted alongside it).
     */
    const val DISCOVERIES_V2_ENABLED = false

    /**
     * Deck Wizard & Engine Rework plan (`docs/plans/deck-wizard-rework-plan.md`), Workstream 1.3 /
     * decision D-E: the Discoveries "Strategies"/"Tribes" sections' per-cluster "Build this" CTA
     * and the Combos tab's "Use as seed" CTA are READ-ONLY for now -- lists render, hand-off CTAs
     * are hidden behind this flag rather than deleted (a future re-enable path once WS 2/3's
     * reworked wizard entry flows land and the hand-off target is ready again). Code stays 100%
     * compiled and reachable; flip to `true` to restore both CTAs.
     */
    const val DISCOVERY_BUILD_HANDOFF_ENABLED = false
}
