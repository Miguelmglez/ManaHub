package com.mmg.manahub.core.model

/**
 * Parsed representation of the Worker's booster.json for a set.
 * Mirrors the §10 schema: a list of weighted booster variants and named card sheets.
 */
data class BoosterConfig(
    val setCode: String,
    val schemaVersion: Int,
    val boosters: List<BoosterVariant>,
    /** Named sheets (e.g. "common", "uncommon", "rareMythic", "foil"). */
    val sheets: Map<String, BoosterSheet>,
)

/** One weighted booster variant; [contents] maps sheet name to slot count. */
data class BoosterVariant(
    val weight: Int,
    val contents: Map<String, Int>,
)

data class BoosterSheet(
    val foil: Boolean,
    val balanceColors: Boolean,
    val cards: List<BoosterCardEntry>,
)

data class BoosterCardEntry(
    val id: String,
    val weight: Int,
)
