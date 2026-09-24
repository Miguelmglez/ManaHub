package com.mmg.manahub.feature.draft.data.remote
// COMMENTS_REVIEWED: 2026-09-22

import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.core.data.local.entity.DraftSetEntity
import com.mmg.manahub.core.data.remote.dto.SetIndexEntryDto

// The Cloudflare index has no separate id, so the set code doubles as the id
fun SetIndexEntryDto.toEntity(): DraftSetEntity = DraftSetEntity(
    id = code,
    code = code,
    name = name,
    releasedAt = releasedAt,
    iconSvgUri = iconSvgUri,
    guideVersion = contentVersions.guide,
    tierListVersion = contentVersions.tierList,
    boosterVersion = contentVersions.booster,
    setImageUrl = setImage,
)

fun DraftSetEntity.toDomain(): DraftSet = DraftSet(
    id = id,
    code = code,
    name = name,
    releasedAt = releasedAt,
    iconSvgUri = iconSvgUri,
    guideVersion = guideVersion,
    tierListVersion = tierListVersion,
    boosterVersion = boosterVersion,
    setImageUrl = setImageUrl,
)
