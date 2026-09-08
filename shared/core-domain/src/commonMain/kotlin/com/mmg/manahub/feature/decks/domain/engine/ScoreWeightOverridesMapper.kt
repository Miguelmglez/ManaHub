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
 * all-null override ([ScoreWeightOverrides.NONE]) produces exactly [defaults] unchanged — zero
 * behavior change when no debug tuning is active. Extends the EXISTING [toScoreWeights] mechanism
 * rather than introducing a second DataStore surface (plan §3.3: "wired through the existing
 * ScoreWeightOverrides DataStore mechanism").
 *
 * @param defaults Deck Analysis Engine v3 (spec §8) — the base every un-overridden field falls back
 *        to. Defaulted to the flat [AnalysisWeights()][AnalysisWeights] for any caller that has no
 *        macro to derive a base from; [com.mmg.manahub.feature.decks.domain.usecase
 *        .EvaluateDeckUseCase] (the one real production caller) always passes
 *        [AnalysisWeights.forMacro] explicitly instead, so a per-field override still wins outright
 *        while every un-overridden field gets the macro-dependent weight, not a flat one.
 */
fun ScoreWeightOverrides.toAnalysisWeights(defaults: AnalysisWeights = AnalysisWeights()): AnalysisWeights {
    return AnalysisWeights(
        manaBase = analysisManaBase ?: defaults.manaBase,
        curve = analysisCurve ?: defaults.curve,
        planRoles = analysisPlanRoles ?: defaults.planRoles,
        synergy = analysisSynergy ?: defaults.synergy,
        legality = analysisLegality ?: defaults.legality,
    )
}
