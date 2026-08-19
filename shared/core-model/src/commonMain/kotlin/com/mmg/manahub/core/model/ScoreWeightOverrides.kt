package com.mmg.manahub.core.model

import com.mmg.manahub.core.model.ScoreWeightOverrides.Companion.NONE


/**
 * Debug-only override of the Deck Doctor scoring weights, persisted in `UserPreferencesDataStore`.
 *
 * This is a CORE-level holder of independent nullable Floats so the `core/data/local` DataStore
 * can persist and emit it without depending on the feature-layer `ScoreWeights`/`AnalysisWeights` (a
 * core → feature import would invert the layering). The feature/decks layer maps it to its own
 * `ScoreWeights` via `ScoreWeightOverrides.toScoreWeights()`, and (Deck Analysis Engine v2 Phase 2)
 * to `AnalysisWeights` via `ScoreWeightOverrides.toAnalysisWeights()` — the SAME extended mechanism,
 * not a second/duplicated DataStore surface.
 *
 * Each field is nullable: `null` means "use the engine default for this weight". The all-null instance
 * ([NONE]) therefore round-trips to exactly `ScoreWeights()`/`AnalysisWeights()` — i.e. zero behavior
 * change at default. These weights are a debug tuning surface only; production builds leave every
 * field null.
 */
data class ScoreWeightOverrides(
    val synergy: Float? = null,
    val roleNeed: Float? = null,
    val curve: Float? = null,
    val power: Float? = null,
    val color: Float? = null,
    val redundancyPenalty: Float? = null,
    val powerFloor: Float? = null,
    // ── Deck Analysis Engine v2 (Phase 2) — the 5 pillar weights, appended LAST so no existing
    // positional-arg constructor call site needs to change. Prefixed `analysis*` to avoid any name
    // collision with the legacy `curve`/`power` fields above (those tune per-card fit scoring; these
    // tune the v2 pillar composite — two different weight spaces sharing one DataStore mechanism).
    val analysisManaBase: Float? = null,
    val analysisCurve: Float? = null,
    val analysisPlanRoles: Float? = null,
    val analysisSynergy: Float? = null,
    val analysisLegality: Float? = null,
) {
    /** True when no field overrides the default — the mapper produces a default `ScoreWeights`
     * (and, for the Phase 2 fields, a default `AnalysisWeights`). */
    val isEmpty: Boolean
        get() = synergy == null && roleNeed == null && curve == null && power == null &&
            color == null && redundancyPenalty == null && powerFloor == null &&
            analysisManaBase == null && analysisCurve == null && analysisPlanRoles == null &&
            analysisSynergy == null && analysisLegality == null

    companion object {
        /** No overrides — maps to the engine-default `ScoreWeights()`/`AnalysisWeights()`. */
        val NONE = ScoreWeightOverrides()
    }
}
