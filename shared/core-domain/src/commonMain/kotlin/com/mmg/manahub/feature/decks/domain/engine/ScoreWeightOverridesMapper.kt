package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.ScoreWeightOverrides

/**
 * Maps the core-level [ScoreWeightOverrides] (seven independent nullable Floats persisted in
 * `UserPreferencesDataStore`) to the feature-layer [ScoreWeights] the scoring engine consumes.
 *
 * Each null field falls back to the corresponding [ScoreWeights] default, so an all-null override
 * ([ScoreWeightOverrides.NONE]) produces exactly `ScoreWeights()` — i.e. zero behavior change when no
 * debug tuning is active. Lives in the feature layer so the core DataStore never depends on
 * [ScoreWeights] (which would invert the core → feature layering).
 */
fun ScoreWeightOverrides.toScoreWeights(): ScoreWeights {
    val defaults = ScoreWeights()
    return ScoreWeights(
        synergy = synergy ?: defaults.synergy,
        roleNeed = roleNeed ?: defaults.roleNeed,
        curve = curve ?: defaults.curve,
        power = power ?: defaults.power,
        color = color ?: defaults.color,
        redundancyPenalty = redundancyPenalty ?: defaults.redundancyPenalty,
        powerFloor = powerFloor ?: defaults.powerFloor,
    )
}

/**
 * Deck Analysis Engine v2 (Phase 2) — maps the SAME core-level [ScoreWeightOverrides] holder's
 * `analysis*` fields to the feature-layer [AnalysisWeights] the v2 pillar composite consumes. An
 * all-null override ([ScoreWeightOverrides.NONE]) produces exactly `AnalysisWeights()` — zero
 * behavior change when no debug tuning is active. Extends the EXISTING [toScoreWeights] mechanism
 * rather than introducing a second DataStore surface (plan §3.3: "wired through the existing
 * ScoreWeightOverrides DataStore mechanism").
 */
fun ScoreWeightOverrides.toAnalysisWeights(): AnalysisWeights {
    val defaults = AnalysisWeights()
    return AnalysisWeights(
        manaBase = analysisManaBase ?: defaults.manaBase,
        curve = analysisCurve ?: defaults.curve,
        planRoles = analysisPlanRoles ?: defaults.planRoles,
        synergy = analysisSynergy ?: defaults.synergy,
        legality = analysisLegality ?: defaults.legality,
    )
}
