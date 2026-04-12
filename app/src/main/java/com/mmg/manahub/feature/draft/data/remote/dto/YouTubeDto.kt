package com.mmg.manahub.feature.draft.data.remote.dto

import com.google.gson.annotations.SerializedName

data class YouTubeSearchResponse(
    @SerializedName("items") val items: List<YouTubeVideoDto>,
)

data class YouTubeVideoDto(
    @SerializedName("id") val id: YouTubeVideoId,
    @SerializedName("snippet") val snippet: YouTubeSnippet,
)

data class YouTubeVideoId(
    @SerializedName("videoId") val videoId: String,
)

data class YouTubeSnippet(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("thumbnails") val thumbnails: YouTubeThumbnails,
    @SerializedName("channelTitle") val channelTitle: String,
    @SerializedName("publishedAt") val publishedAt: String,
)

data class YouTubeThumbnails(
    @SerializedName("medium") val medium: YouTubeThumbnail?,
    @SerializedName("high") val high: YouTubeThumbnail?,
)

data class YouTubeThumbnail(
    @SerializedName("url") val url: String,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?,
)
