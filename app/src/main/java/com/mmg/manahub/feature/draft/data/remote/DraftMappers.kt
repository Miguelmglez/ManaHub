package com.mmg.manahub.feature.draft.data.remote

import com.mmg.manahub.core.data.remote.dto.ScryfallSetDto
import com.mmg.manahub.feature.draft.data.local.DraftSetEntity
import com.mmg.manahub.feature.draft.data.remote.dto.YouTubeVideoDto
import com.mmg.manahub.feature.draft.domain.model.DraftSet
import com.mmg.manahub.feature.draft.domain.model.DraftVideo

fun ScryfallSetDto.toEntity(): DraftSetEntity = DraftSetEntity(
    id = id,
    code = code,
    name = name,
    setType = setType,
    releasedAt = releasedAt ?: "",
    iconSvgUri = iconSvgUri,
    cardCount = cardCount,
    scryfallUri = scryfallUri,
)

fun DraftSetEntity.toDomain(): DraftSet = DraftSet(
    id = id,
    code = code,
    name = name,
    setType = setType,
    releasedAt = releasedAt,
    iconSvgUri = iconSvgUri,
    cardCount = cardCount,
    scryfallUri = scryfallUri,
)

fun YouTubeVideoDto.toDomain(): DraftVideo = DraftVideo(
    videoId = id.videoId,
    title = snippet.title,
    description = snippet.description,
    thumbnailUrl = snippet.thumbnails.high?.url ?: snippet.thumbnails.medium?.url ?: "",
    channelName = snippet.channelTitle,
    publishedAt = snippet.publishedAt,
)
