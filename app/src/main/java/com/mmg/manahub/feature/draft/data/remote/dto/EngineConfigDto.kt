package com.mmg.manahub.feature.draft.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO mirroring the Worker's `engine.json`. All fields are nullable so a partially-formed
 * config degrades gracefully (parsed in [com.mmg.manahub.feature.draft.data.DraftSimRepositoryImpl]).
 */
data class EngineConfigDto(
    @SerializedName("setCode") val setCode: String?,
    @SerializedName("schemaVersion") val schemaVersion: Int?,
    @SerializedName("lastUpdated") val lastUpdated: String?,
    @SerializedName("params") val params: EngineParamsDto?,
    @SerializedName("archetypes") val archetypes: List<EngineArchetypeDto>?,
    /** Keyed by Scryfall id. */
    @SerializedName("cards") val cards: Map<String, EngineCardSignalsDto>?,
)

data class EngineParamsDto(
    @SerializedName("ratingWeight") val ratingWeight: Float?,
    @SerializedName("synergyWeight") val synergyWeight: Float?,
    @SerializedName("opennessWeight") val opennessWeight: Float?,
    @SerializedName("fixingBonus") val fixingBonus: Float?,
    @SerializedName("curveWeight") val curveWeight: Float?,
    @SerializedName("commitmentThreshold") val commitmentThreshold: Float?,
    @SerializedName("speculationPicks") val speculationPicks: Int?,
)

data class EngineArchetypeDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("colors") val colors: List<String>?,
    @SerializedName("tier") val tier: Int?,
    @SerializedName("opennessBase") val opennessBase: Float?,
    @SerializedName("keyCardIds") val keyCardIds: List<String>?,
)

data class EngineCardSignalsDto(
    @SerializedName("archetypeWeights") val archetypeWeights: Map<String, Float>?,
    @SerializedName("rating") val rating: Float?,
    @SerializedName("fixing") val fixing: Boolean?,
    @SerializedName("removal") val removal: Boolean?,
    @SerializedName("evasion") val evasion: Boolean?,
    @SerializedName("bomb") val bomb: Boolean?,
)
