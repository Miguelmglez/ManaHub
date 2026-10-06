package com.mmg.manahub.core.model.news

/** A device-local snapshot of a saved article or video; outlives feed eviction and source removal. */
data class SavedNewsItem(
    val item: NewsItem,
    val savedAt: Long,
)
