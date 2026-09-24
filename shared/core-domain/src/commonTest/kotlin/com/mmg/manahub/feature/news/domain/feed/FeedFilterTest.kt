package com.mmg.manahub.feature.news.domain.feed

import com.mmg.manahub.core.model.news.FeedContentFilter
import com.mmg.manahub.core.model.news.NewsItem
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedFilterTest {

    private fun article(id: String, sourceId: String, title: String = "Title $id", description: String = "") = NewsItem.Article(
        id = id, title = title, description = description, imageUrl = null, publishedAt = 0L,
        sourceName = "Source $sourceId", sourceId = sourceId, url = "https://example.com/$id", author = null,
    )

    private fun video(id: String, sourceId: String) = NewsItem.Video(
        id = id, title = "Video $id", description = "", imageUrl = null, publishedAt = 0L,
        sourceName = "Channel $sourceId", sourceId = sourceId, url = "https://example.com/$id",
        videoId = id, channelName = "Channel",
    )

    private val items = listOf(
        article("a1", "s1", title = "Standard banlist update"),
        article("a2", "s2", description = "A deep dive into Modern"),
        video("v1", "s1"),
        video("v2", "s3"),
    )

    private fun ids(
        followed: Set<String> = setOf("s1", "s2", "s3"),
        filter: FeedContentFilter = FeedContentFilter.ALL,
        selected: String? = null,
        query: String = "",
        names: Map<String, String> = emptyMap(),
    ) = filterFeed(items, followed, filter, selected, query, names).map { it.id }

    @Test
    fun given_unfollowedSource_then_itsItemsAreHidden() {
        assertEquals(listOf("a1", "a2", "v1"), ids(followed = setOf("s1", "s2")))
    }

    @Test
    fun given_contentFilter_then_onlyThatKindIsShown() {
        assertEquals(listOf("a1", "a2"), ids(filter = FeedContentFilter.ARTICLES))
        assertEquals(listOf("v1", "v2"), ids(filter = FeedContentFilter.VIDEOS))
    }

    @Test
    fun given_selectedSource_then_onlyItsItemsAreShown() {
        assertEquals(listOf("a1", "v1"), ids(selected = "s1"))
    }

    @Test
    fun given_query_then_titleDescriptionAndSourceNameAreSearchedIgnoringCase() {
        assertEquals(listOf("a1"), ids(query = "  BANLIST "))
        assertEquals(listOf("a2"), ids(query = "modern"))
        assertEquals(listOf("v2"), ids(query = "goldfish", names = mapOf("s3" to "MTGGoldfish")))
    }

    @Test
    fun given_duplicateIds_then_eachItemAppearsOnce() {
        val duplicated = items + article("a1", "s1")

        assertEquals(listOf("a1", "a2", "v1", "v2"), filterFeed(duplicated, setOf("s1", "s2", "s3"), FeedContentFilter.ALL, null, "", emptyMap()).map { it.id })
    }

    @Test
    fun given_nothingFollowed_then_theFeedIsEmpty() {
        assertEquals(emptyList(), ids(followed = emptySet()))
    }
}
