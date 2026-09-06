package com.mmg.manahub.core.data.remote.collection

import com.mmg.manahub.core.data.sync.KeysetPageRow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO mirroring the `user_card_collection` Supabase table.
 *
 * All timestamps are epoch millis (BIGINT in Postgres = Long in Kotlin).
 * No Instant serialization needed — Supabase stores and returns them as plain integers.
 *
 * KMP web roadmap W3d: moved to `:shared:core-data` commonMain. The Room-entity mapping
 * extensions (`toEntity`/`toDto`) stayed in `:app` (`UserCardCollectionMappers.kt`, Room has no
 * wasmJs target) — this file only carries the pure Supabase wire shape.
 *
 * Implements [KeysetPageRow] (collection sync data-loss fix, `linear-moseying-yeti` plan, Phase
 * 3) so [com.mmg.manahub.core.data.sync.drainPages] can advance the `get_collection_changes_page`
 * cursor off this DTO directly.
 */
@Serializable
data class UserCardCollectionDto(
    override val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("scryfall_id") val scryfallId: String,
    val quantity: Int,
    @SerialName("is_foil") val isFoil: Boolean,
    val condition: String,
    val language: String,
    @SerialName("is_for_trade") val isForTrade: Boolean,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("updated_at") override val updatedAt: Long,
    @SerialName("created_at") val createdAt: Long,
) : KeysetPageRow
