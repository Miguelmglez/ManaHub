package com.mmg.manahub.feature.draft.domain.model

data class DraftSet(
    val id: String,
    val code: String,
    val name: String,
    val setType: String,
    val releasedAt: String,
    val iconSvgUri: String,
    val cardCount: Int,
    val scryfallUri: String,
)
