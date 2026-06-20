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
