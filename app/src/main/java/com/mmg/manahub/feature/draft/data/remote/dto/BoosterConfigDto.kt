package com.mmg.manahub.feature.draft.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO mirroring the Worker's booster.json (§10 schema):
 * ```
 * {
 *   "setCode": "tdm",
 *   "schemaVersion": 1,
 *   "boosters": [ { "weight": 1, "contents": { "common": 10, "uncommon": 3, ... } } ],
 *   "sheets": {
 *     "common": { "foil": false, "balanceColors": true, "cards": { "<scryfallId>": <weight>, ... } },
 *     ...
 *   }
 * }
 * ```
 *
 * `sheets[*].cards` is a JSON object keyed by scryfallId with integer weights — Gson
 * deserialises it as a `Map<String, Int>`, which is mapped to the domain
 * `List<BoosterCardEntry>` in the repository.
 */
data class BoosterConfigDto(
    @SerializedName("setCode") val setCode: String?,
    @SerializedName("schemaVersion") val schemaVersion: Int?,
    @SerializedName("boosters") val boosters: List<BoosterVariantDto>?,
    @SerializedName("sheets") val sheets: Map<String, BoosterSheetDto>?,
)

data class BoosterVariantDto(
    @SerializedName("weight") val weight: Int?,
    @SerializedName("contents") val contents: Map<String, Int>?,
)

data class BoosterSheetDto(
    @SerializedName("foil") val foil: Boolean?,
    @SerializedName("balanceColors") val balanceColors: Boolean?,
    @SerializedName("cards") val cards: Map<String, Int>?,
)
