package com.mmg.manahub.core.model

data class PaginatedCards(
    val cards: List<Card>,
    val hasMore: Boolean
)
