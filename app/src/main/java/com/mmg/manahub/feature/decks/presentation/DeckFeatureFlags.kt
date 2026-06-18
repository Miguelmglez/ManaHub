package com.mmg.manahub.feature.decks.presentation

/**
 * Compile-time UI feature flags for the Decks feature. These surfaces are HIDDEN for the
 * current release and will be re-enabled once polished. Flip a flag to `true` to restore the
 * corresponding UI entry point. See docs/hidden-features/ for the full re-enable checklist
 * per feature. Hiding is UI-only — all underlying logic/ViewModels/composables stay compiled.
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
