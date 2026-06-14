package com.mmg.manahub.core.domain.model

/**
 * Debug-only override of the Deck Doctor scoring weights, persisted in `UserPreferencesDataStore`.
 *
 * This is a CORE-level holder of seven independent nullable Floats so the `core/data/local` DataStore
 * can persist and emit it without depending on the feature-layer `ScoreWeights` (a core → feature
 * import would invert the layering). The feature/decks layer maps it to its own `ScoreWeights` via
 * `ScoreWeightOverrides.toScoreWeights()`.
 *
 * Each field is nullable: `null` means "use the engine default for this weight". The all-null instance
 * ([NONE]) therefore round-trips to exactly `ScoreWeights()` — i.e. zero behavior change at default.
 * These weights are a debug tuning surface only; production builds leave every field null.
 */
data class ScoreWeightOverrides(
    val synergy: Float? = null,
    val roleNeed: Float? = null,
    val curve: Float? = null,
    val power: Float? = null,
    val color: Float? = null,
    val redundancyPenalty: Float? = null,
    val powerFloor: Float? = null,
) {
    /** True when no field overrides the default — the mapper produces a default `ScoreWeights`. */
    val isEmpty: Boolean
        get() = synergy == null && roleNeed == null && curve == null && power == null &&
            color == null && redundancyPenalty == null && powerFloor == null

    companion object {
        /** No overrides — maps to the engine-default `ScoreWeights()`. */
        val NONE = ScoreWeightOverrides()
    }
}
