package com.mmg.manahub.feature.draft.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class YouTubeSearchResponse(
    @SerialName("items") val items: List<YouTubeVideoDto>,
)

@Serializable
data class YouTubeVideoDto(
    @SerialName("id") val id: YouTubeVideoId,
    @SerialName("snippet") val snippet: YouTubeSnippet,
)

@Serializable
data class YouTubeVideoId(
    @SerialName("videoId") val videoId: String,
)

@Serializable
data class YouTubeSnippet(
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("thumbnails") val thumbnails: YouTubeThumbnails,
    @SerialName("channelTitle") val channelTitle: String,
    @SerialName("publishedAt") val publishedAt: String,
)

@Serializable
data class YouTubeThumbnails(
    @SerialName("medium") val medium: YouTubeThumbnail? = null,
    @SerialName("high") val high: YouTubeThumbnail? = null,
)

@Serializable
data class YouTubeThumbnail(
    @SerialName("url") val url: String,
    @SerialName("width") val width: Int? = null,
    @SerialName("height") val height: Int? = null,
)
