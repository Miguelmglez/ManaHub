package com.mmg.manahub.feature.draft.presentation

/**
 * Compile-time UI feature flags for the Draft feature. The Draft Simulator is HIDDEN for the
 * current release (the Draft Guide / tier lists stay visible). Flip [SIMULATOR_ENABLED] to
 * `true` to restore the "Simulate Draft" entry point. See docs/hidden-features/. UI-only hide.
 */
object DraftFeatureFlags {
    /** The "Simulate Draft" button on the set-detail screen + the sim flow entry. */
    const val SIMULATOR_ENABLED = false
}
