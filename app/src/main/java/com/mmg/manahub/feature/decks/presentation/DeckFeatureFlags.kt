package com.mmg.manahub.feature.decks.presentation

/**
 * Compile-time UI feature flags for the Decks feature. Flip a flag to `false` to hide the
 * corresponding UI entry point again. See docs/hidden-features/ for the re-enable checklist
 * per feature. Hiding is UI-only — all underlying logic/ViewModels/composables stay compiled.
 *
 * [PLAYTEST_ENABLED] and [DECK_STUDIO_SUGGESTIONS_TAB_ENABLED] were hidden 2026-07-14 for an
 * upcoming release and are UNCHANGED by this pass.
 *
 * **2026-07-17 (Deck Builder v2 Phase 3/5 flag restructure, plan D10/§3.7):**
 * [DECK_BUILDER_V2_ENABLED] and [DISCOVERIES_V2_ENABLED] flip ON as their respective surfaces land
 * (wizard + generation/result screens; Discoveries v2 clustering). Their LEGACY counterparts,
 * [DECK_STUDIO_BUILD_FROM_SEED_ENABLED] and [DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED], flip OFF —
 * code stays compiled and reachable by flipping them back on (comparison sessions, plan §6 pt 4).
 * The Deck Studio entry points ("Build from seed" menu item / empty-state button, "Browse
 * inspirations" menu item / empty-state button) stay VISIBLE whenever EITHER the v2 flag OR its
 * legacy sibling is on — only the DESTINATION (wizard vs. old seed sheet; Discoveries v2 vs. old
 * Inspirations content) switches on which flag is actually true. See `DeckStudioScreen.kt`'s
 * `entryPointVisible`/`useWizardEntry` helpers.
 */
object DeckFeatureFlags {
    /** Deck Playtest entry points (DeckList per-deck button + Deck Studio top-bar button). */
    const val PLAYTEST_ENABLED = false

    /** Deck Studio "Suggestions" tab (inline Deck Doctor). */
    const val DECK_STUDIO_SUGGESTIONS_TAB_ENABLED = true

    /** Deck Studio "Build from seed" (legacy sheet) — OFF now that [DECK_BUILDER_V2_ENABLED] wizard
     * has landed (plan D10/§3.7). Code stays compiled; flip back to `true` to re-enable for a
     * side-by-side comparison session (the entry point routes to whichever flag is on, preferring
     * the v2 wizard when both are true). */
    const val DECK_STUDIO_BUILD_FROM_SEED_ENABLED = false

    /** Deck Studio "Browse inspirations" (legacy `discoverSynergies` content) — OFF now that
     * [DISCOVERIES_V2_ENABLED] has landed (plan D10/§3.7). Code stays compiled. */
    const val DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED = false

    /**
     * Deck Builder v2 (`docs/plans/deck-builder-v2-plan.md`) wizard + generation + result screens.
     * Phase 3 landed 2026-07-17: `Screen.DeckWizard` + `DeckWizardViewModel` + the 4-step wizard/
     * generation/result composables. The Deck Studio "Build from seed" entry point now routes here
     * instead of the legacy seed sheet.
     */
    const val DECK_BUILDER_V2_ENABLED = true

    /**
     * Deck Builder v2 Phase 5 (plan §3.5): `DiscoverSynergiesV2UseCase` — identity-only clustering
     * (STRATEGY/ARCHETYPE tags + derived `tribe:<x>` keys), ranked by [com.mmg.manahub.feature.decks
     * .domain.engine.DeckScorer.fit], color-coherent. Replaces the legacy `discoverSynergies` content
     * inside the SAME "Browse inspirations" entry point (D11) — "Build this" hands off to the v2
     * wizard pre-filled with strategy/tribe + colors, replacing the old seed-sheet handoff.
     */
    const val DISCOVERIES_V2_ENABLED = true
}
