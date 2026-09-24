package com.mmg.manahub.feature.news.domain.feed

import com.mmg.manahub.core.model.news.FeedContentFilter
import com.mmg.manahub.core.model.news.NewsItem

/** Followed-only items, narrowed by type, an optional single source and a title/description/source-name query; unique by id. */
fun filterFeed(
    items: List<NewsItem>,
    followedIds: Set<String>,
    contentFilter: FeedContentFilter,
    selectedSourceId: String?,
    query: String,
    sourceNames: Map<String, String>,
): List<NewsItem> {
    val needle = query.trim()
    return items.asSequence()
        .filter { it.sourceId in followedIds }
        .filter { selectedSourceId == null || it.sourceId == selectedSourceId }
        .filter { it.matches(contentFilter) }
        .filter { needle.isEmpty() || it.matches(needle, sourceNames[it.sourceId] ?: it.sourceName) }
        .distinctBy { it.id }
        .toList()
}

fun NewsItem.matches(filter: FeedContentFilter): Boolean = when (filter) {
    FeedContentFilter.ALL -> true
    FeedContentFilter.ARTICLES -> this is NewsItem.Article
    FeedContentFilter.VIDEOS -> this is NewsItem.Video
}

private fun NewsItem.matches(needle: String, sourceName: String): Boolean =
    title.contains(needle, ignoreCase = true) ||
        description.contains(needle, ignoreCase = true) ||
        sourceName.contains(needle, ignoreCase = true)
