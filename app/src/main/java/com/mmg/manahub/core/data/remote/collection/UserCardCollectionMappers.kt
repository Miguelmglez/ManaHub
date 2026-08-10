package com.mmg.manahub.core.data.remote.collection

import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity

/**
 * Room-entity mapping extensions for [UserCardCollectionDto] (KMP web roadmap W3d).
 *
 * The DTO itself and the [CollectionRemoteDataSource] contract it serves moved to
 * `:shared:core-data` commonMain — both are pure Supabase wire shapes with zero Room dependency.
 * These two extension functions stay here (androidMain-equivalent) because Room has no wasmJs
 * target: [UserCardCollectionEntity] only exists on Android. [UserCardCollectionDto] is resolved
 * here via same-package visibility (no import needed — it's declared in `:shared:core-data`'s
 * commonMain, which `:app` depends on).
 */

/** Maps a remote DTO to the local Room entity. */
fun UserCardCollectionDto.toEntity(): UserCardCollectionEntity = UserCardCollectionEntity(
    id = id,
    userId = userId,
    scryfallId = scryfallId,
    quantity = quantity,
    isFoil = isFoil,
    condition = condition,
    language = language,
    isForTrade = isForTrade,
    isDeleted = isDeleted,
    updatedAt = updatedAt,
    createdAt = createdAt,
)

/** Maps a local Room entity to the remote DTO for upload. */
fun UserCardCollectionEntity.toDto(): UserCardCollectionDto = UserCardCollectionDto(
    id = id,
    userId = userId ?: "",
    scryfallId = scryfallId,
    quantity = quantity,
    isFoil = isFoil,
    condition = condition,
    language = language,
    isForTrade = isForTrade,
    isDeleted = isDeleted,
    updatedAt = updatedAt,
    createdAt = createdAt,
)
