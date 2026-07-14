package com.mmg.manahub.feature.decks.presentation

/**
 * Compile-time UI feature flags for the Decks feature. Flip a flag to `false` to hide the
 * corresponding UI entry point again. See docs/hidden-features/ for the re-enable checklist
 * per feature. Hiding is UI-only — all underlying logic/ViewModels/composables stay compiled.
 *
 * [PLAYTEST_ENABLED], [DECK_STUDIO_SUGGESTIONS_TAB_ENABLED], and [DECK_STUDIO_BUILD_FROM_SEED_ENABLED]
 * were hidden again 2026-07-14 for an upcoming release — the underlying features (Motor A/Motor B
 * suggestions, seed-build, playtest) are still being polished. Re-enable by flipping the flag back
 * to `true`; nothing was deleted. [DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED] stays off (unrelated to
 * this pass — Discoveries was already hidden).
 */
object DeckFeatureFlags {
    /** Deck Playtest entry points (DeckList per-deck button + Deck Studio top-bar button). */
    const val PLAYTEST_ENABLED = false

    /** Deck Studio "Suggestions" tab (inline Deck Doctor). */
    const val DECK_STUDIO_SUGGESTIONS_TAB_ENABLED = false

    /** Deck Studio "Build from seed" (overflow menu item + empty-state primary button). */
    const val DECK_STUDIO_BUILD_FROM_SEED_ENABLED = false

    /** Deck Studio "Browse inspirations" / Discoveries (overflow item + empty-state button). */
    const val DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED = false
}
