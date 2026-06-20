package com.mmg.manahub.feature.draft.data.remote

import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.core.data.local.entity.DraftSetEntity
import com.mmg.manahub.feature.draft.data.remote.dto.SetIndexEntryDto
import com.mmg.manahub.feature.draft.data.remote.dto.YouTubeVideoDto
import com.mmg.manahub.feature.draft.domain.model.DraftVideo

/**
 * Maps a [SetIndexEntryDto] from the Cloudflare sets-index.json to a [DraftSetEntity]
 * for Room persistence. The set code is used as both id and code for consistency
 * with the new Cloudflare-based data source.
 */
fun SetIndexEntryDto.toEntity(): DraftSetEntity = DraftSetEntity(
    id = code,
    code = code,
    name = name,
    releasedAt = releasedAt,
    iconSvgUri = iconSvgUri,
    guideVersion = contentVersions.guide,
    tierListVersion = contentVersions.tierList,
    boosterVersion = contentVersions.booster,
)

/**
 * Maps a [DraftSetEntity] from Room to the domain [DraftSet] model.
 */
fun DraftSetEntity.toDomain(): DraftSet = DraftSet(
    id = id,
    code = code,
    name = name,
    releasedAt = releasedAt,
    iconSvgUri = iconSvgUri,
    guideVersion = guideVersion,
    tierListVersion = tierListVersion,
    boosterVersion = boosterVersion,
)

/**
 * Maps a [YouTubeVideoDto] from the YouTube Data API to the domain [DraftVideo] model.
 */
fun YouTubeVideoDto.toDomain(): DraftVideo = DraftVideo(
    videoId = id.videoId,
    title = snippet.title,
    description = snippet.description,
    thumbnailUrl = snippet.thumbnails.high?.url ?: snippet.thumbnails.medium?.url ?: "",
    channelName = snippet.channelTitle,
    publishedAt = snippet.publishedAt,
)
