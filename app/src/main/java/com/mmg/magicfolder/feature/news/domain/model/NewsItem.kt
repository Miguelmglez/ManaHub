package com.mmg.magicfolder.feature.news.domain.model

sealed class NewsItem {
    abstract val id: String
    abstract val title: String
    abstract val description: String
    abstract val imageUrl: String?
    abstract val publishedAt: Long
    abstract val sourceName: String
    abstract val sourceId: String
    abstract val url: String

    data class Article(
        override val id: String,
        override val title: String,
        override val description: String,
        override val imageUrl: String?,
        override val publishedAt: Long,
        override val sourceName: String,
        override val sourceId: String,
        override val url: String,
        val author: String?,
    ) : NewsItem()

    data class Video(
        override val id: String,
        override val title: String,
        override val description: String,
        override val imageUrl: String?,
        override val publishedAt: Long,
        override val sourceName: String,
        override val sourceId: String,
        override val url: String,
        val videoId: String,
        val channelName: String,
        val duration: String? = null,
    ) : NewsItem()
}
