package com.mmg.manahub.feature.draft.domain.model

data class SetDraftGuide(
    val setCode: String,
    val setName: String,
    val lastUpdated: String,
    val overview: String,
    val mechanics: List<MechanicGuide>,
    val archetypes: List<ArchetypeGuide>,
    val topCommons: Map<String, List<String>>,
    val topUncommons: Map<String, List<String>>,
    val generalTips: List<String>,
)

data class MechanicGuide(
    val name: String,
    val description: String,
    val draftTip: String,
)

data class ArchetypeGuide(
    val colors: String,
    val name: String,
    val tier: String,
    val description: String,
    val keyCommons: List<String>,
    val keyUncommons: List<String>,
    val strategy: String,
)
