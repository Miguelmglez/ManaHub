package com.mmg.magicfolder.feature.draft.domain.model

data class SetTierList(
    val setCode: String,
    val setName: String,
    val lastUpdated: String,
    val tiers: List<TierGroup>,
)

data class TierGroup(
    val tier: String,
    val label: String,
    val description: String,
    val cards: List<TierCard>,
)

data class TierCard(
    val name: String,
    val color: String,
    val rarity: String,
    val reason: String,
)
