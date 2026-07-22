package com.mmg.manahub.feature.decks.presentation

/**
 * Compile-time UI feature flags for the Decks feature. Flip a flag to `false` to hide the
 * corresponding UI entry point again. See docs/hidden-features/ for the re-enable checklist
 * per feature. Hiding is UI-only — all underlying logic/ViewModels/composables stay compiled.
 *
 * [PLAYTEST_ENABLED] was hidden 2026-07-14 for an upcoming release and is UNCHANGED by this pass.
 *
 * **2026-07-17 (Deck Builder v2 Phase 3/5 flag restructure, plan D10/§3.7):**
 * [DECK_BUILDER_V2_ENABLED] and [DISCOVERIES_V2_ENABLED] flipped ON as their respective surfaces
 * landed (wizard + generation/result screens; Discoveries v2 clustering), with their LEGACY
 * counterparts ([DECK_STUDIO_BUILD_FROM_SEED_ENABLED] / [DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED])
 * flipped OFF. `DECK_STUDIO_SUGGESTIONS_TAB_ENABLED` was ALSO flipped back to `true` some time
 * after 2026-07-14 (for the Community/Archetype plan Suggestions-tab work) — see
 * `project_deck_builder_v2` memory for the exact point in that campaign.
 *
 * **2026-07-21 (temporary hide, this pass — distinct from the 2026-07-14 permanent-release hide
 * batch):** [DECK_STUDIO_SUGGESTIONS_TAB_ENABLED], [DECK_BUILDER_V2_ENABLED], and
 * [DISCOVERIES_V2_ENABLED] are ALL flipped back to `false`. Because the legacy siblings
 * ([DECK_STUDIO_BUILD_FROM_SEED_ENABLED] / [DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED]) were already
 * `false`, this fully hides the "Build from seed" and "Browse inspirations" entry points
 * end-to-end (both the v2 AND legacy call sites are now gated off — no destination is reachable
 * from either entry point) and hides the inline Suggestions tab. Code stays 100% compiled and
 * reachable; flip any subset of these three back to `true` to re-enable that surface. See
 * `DeckStudioScreen.kt`'s `handleBuildFromSeed`/`seedEnabled`/`inspirationsEnabled` OR-gates —
 * they stay written as "legacy OR v2" so re-enabling the legacy flag alone (without touching the
 * v2 flag) still works for a side-by-side comparison session.
 */
object DeckFeatureFlags {
    /** Deck Playtest entry points (DeckList per-deck button + Deck Studio top-bar button). */
    const val PLAYTEST_ENABLED = false

    /** Deck Studio "Suggestions" tab (inline Deck Doctor). Hidden 2026-07-21 (temporary — was
     * `true` since the Community/Archetype plan Suggestions-tab launch). Flip to `true` to
     * re-enable; see `docs/hidden-features/deck-studio-suggestions.md`. */
    const val DECK_STUDIO_SUGGESTIONS_TAB_ENABLED = false

    /** Deck Studio "Build from seed" (legacy sheet) — OFF since [DECK_BUILDER_V2_ENABLED] wizard
     * landed (plan D10/§3.7). Code stays compiled; flip back to `true` to re-enable for a
     * side-by-side comparison session (the entry point routes to whichever flag is on, preferring
     * the v2 wizard when both are true). */
    const val DECK_STUDIO_BUILD_FROM_SEED_ENABLED = false

    /** Deck Studio "Browse inspirations" (legacy `discoverSynergies` content) — OFF since
     * [DISCOVERIES_V2_ENABLED] landed (plan D10/§3.7). Code stays compiled. */
    const val DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED = false

    /**
     * Deck Builder v2 (`docs/plans/deck-builder-v2-plan.md`) wizard + generation + result screens.
     * Phase 3 landed 2026-07-17: `Screen.DeckWizard` + `DeckWizardViewModel` + the 4-step wizard/
     * generation/result composables. **Hidden 2026-07-21 (temporary)** — with
     * [DECK_STUDIO_BUILD_FROM_SEED_ENABLED] already `false`, this fully hides the "Build from seed"
     * entry point end-to-end. Flip back to `true` to restore it routing to the v2 wizard; see
     * `docs/hidden-features/deck-studio-build-from-seed.md`.
     */
    const val DECK_BUILDER_V2_ENABLED = false

    /**
     * Deck Builder v2 Phase 5 (plan §3.5): `DiscoverSynergiesV2UseCase` — identity-only clustering
     * (STRATEGY/ARCHETYPE tags + derived `tribe:<x>` keys), ranked by [com.mmg.manahub.feature.decks
     * .domain.engine.DeckScorer.fit], color-coherent. Replaces the legacy `discoverSynergies` content
     * inside the SAME "Browse inspirations" entry point (D11) — "Build this" hands off to the v2
     * wizard pre-filled with strategy/tribe + colors, replacing the old seed-sheet handoff.
     * **Hidden 2026-07-21 (temporary)** — with [DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED] already
     * `false`, this fully hides the "Browse inspirations" entry point end-to-end. Flip back to
     * `true` to restore it; see `docs/hidden-features/deck-studio-inspirations.md`.
     */
    const val DISCOVERIES_V2_ENABLED = false
}
