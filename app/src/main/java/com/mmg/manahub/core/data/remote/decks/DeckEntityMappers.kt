package com.mmg.manahub.core.data.remote.decks

import com.mmg.manahub.core.data.local.entity.DeckCardEntity
import com.mmg.manahub.core.data.local.entity.DeckEntity

/**
 * Room-entity mapping extensions for [DeckSyncDto]/[DeckCardSyncDto] (KMP web roadmap W3c).
 *
 * The DTOs themselves and the [DeckRemoteDataSource] contract they serve moved to
 * `:shared:core-data` commonMain — both are pure Supabase wire shapes with zero Room dependency.
 * These three extension functions stay here (androidMain-equivalent) because Room has no wasmJs
 * target: [DeckEntity]/[DeckCardEntity] only exist on Android. `DeckSyncDto`/`DeckCardSyncDto` are
 * resolved here via same-package visibility (no import needed — they're declared in
 * `:shared:core-data`'s commonMain, which `:app` depends on).
 */

/** Maps a remote [DeckSyncDto] to the local Room entity. */
fun DeckSyncDto.toEntity(): DeckEntity = DeckEntity(
    id = id,
    userId = userId,
    name = name,
    description = description,
    format = format,
    coverCardId = coverCardId,
    commanderCardId = commanderCardId,
    isDeleted = isDeleted,
    updatedAt = updatedAt,
    createdAt = createdAt,
    strategyLocked = strategyLocked,
)

/** Maps a local Room [DeckEntity] to the remote DTO for upload. */
fun DeckEntity.toDto(): DeckSyncDto = DeckSyncDto(
    id = id,
    userId = userId ?: "",
    name = name,
    description = description,
    format = format,
    coverCardId = coverCardId,
    commanderCardId = commanderCardId,
    isDeleted = isDeleted,
    updatedAt = updatedAt,
    createdAt = createdAt,
    strategyLocked = strategyLocked,
)

/** Maps a local Room [DeckCardEntity] to the remote DTO for upload. */
fun DeckCardEntity.toSyncDto(): DeckCardSyncDto = DeckCardSyncDto(
    scryfallId = scryfallId,
    quantity = quantity,
    isSideboard = isSideboard,
    source = source,
)
