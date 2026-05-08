package com.mmg.manahub.core.data.remote.collection

import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO mirroring the `user_card_collection` Supabase table.
 *
 * All timestamps are epoch millis (BIGINT in Postgres = Long in Kotlin).
 * No Instant serialization needed — Supabase stores and returns them as plain integers.
 */
@Serializable
data class UserCardCollectionDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("scryfall_id") val scryfallId: String,
    val quantity: Int,
    @SerialName("is_foil") val isFoil: Boolean,
    val condition: String,
    val language: String,
    @SerialName("is_alternative_art") val isAlternativeArt: Boolean,
    @SerialName("is_for_trade") val isForTrade: Boolean,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("created_at") val createdAt: Long,
)

/** Maps a remote DTO to the local Room entity. */
fun UserCardCollectionDto.toEntity(): UserCardCollectionEntity = UserCardCollectionEntity(
    id = id,
    userId = userId,
    scryfallId = scryfallId,
    quantity = quantity,
    isFoil = isFoil,
    condition = condition,
    language = language,
    isAlternativeArt = isAlternativeArt,
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
    isAlternativeArt = isAlternativeArt,
    isForTrade = isForTrade,
    isDeleted = isDeleted,
    updatedAt = updatedAt,
    createdAt = createdAt,
)
