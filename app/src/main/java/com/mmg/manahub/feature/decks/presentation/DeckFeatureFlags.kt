package com.mmg.manahub.feature.decks.presentation

/**
 * Compile-time UI feature flags for the Decks feature. Flip a flag to `false` to hide the
 * corresponding UI entry point again. See docs/hidden-features/ for the re-enable checklist
 * per feature. Hiding is UI-only — all underlying logic/ViewModels/composables stay compiled.
 *
 * [DECK_STUDIO_SUGGESTIONS_TAB_ENABLED] and [DECK_STUDIO_BUILD_FROM_SEED_ENABLED] were re-enabled
 * 2026-07-12: the Deck Doctor Community/Archetype plan (Motor A + Motor B) is fully implemented
 * and the `manahub-community` Cloudflare Worker is deployed at a real URL — the user explicitly
 * requested the whole feature be visible. [DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED] stays off
 * (Discoveries was not part of this request).
 */
object DeckFeatureFlags {
    /** Deck Playtest entry points (DeckList per-deck button + Deck Studio top-bar button). */
    const val PLAYTEST_ENABLED = true

    /** Deck Studio "Suggestions" tab (inline Deck Doctor). */
    const val DECK_STUDIO_SUGGESTIONS_TAB_ENABLED = true

    /** Deck Studio "Build from seed" (overflow menu item + empty-state primary button). */
    const val DECK_STUDIO_BUILD_FROM_SEED_ENABLED = true

    /** Deck Studio "Browse inspirations" / Discoveries (overflow item + empty-state button). */
    const val DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED = false
}
