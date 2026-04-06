package com.mmg.magicfolder.feature.draft.domain.model

data class DraftVideo(
    val videoId: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val channelName: String,
    val publishedAt: String,
)
