package com.mmg.manahub.core.data.remote.decks

import com.mmg.manahub.core.data.sync.KeysetPageRow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO mirroring the `decks` Supabase table.
 *
 * All timestamps are epoch millis (BIGINT in Postgres).
 *
 * KMP web roadmap W3c: moved to `:shared:core-data` commonMain alongside [DeckRemoteDataSource]
 * (both platforms serialize/deserialize the same wire shape). The Room-entity mapping extensions
 * ([toEntity]-equivalent — `DeckEntity`/`DeckCardEntity` are androidMain-only, Room has no wasmJs
 * target) stay in `:app`'s `core/data/remote/decks/DeckEntityMappers.kt`.
 *
 * Implements [KeysetPageRow] (collection sync data-loss fix, `linear-moseying-yeti` plan, Phase 3)
 * so [com.mmg.manahub.core.data.sync.drainPages] can advance the `get_deck_changes_page` cursor
 * off this DTO directly.
 */
@Serializable
data class DeckSyncDto(
    override val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val description: String,
    val format: String,
    @SerialName("cover_card_id") val coverCardId: String?,
    @SerialName("commander_card_id") val commanderCardId: String?,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("updated_at") override val updatedAt: Long,
    @SerialName("created_at") val createdAt: Long,
    /**
     * Deck Engine Unification plan (Phase 0.2/0.4) — the D4 lock flag. Defaulted `false` so
     * decoding a response from a server that has not yet added this column (migration landing in
     * parallel) is tolerant: a missing/absent JSON key falls back to `false`, never a decode
     * failure. `archetype_override`/`themes_override`/`tribe_override` are NOT synced (same
     * local-only convention as before this plan — see [com.mmg.manahub.core.model.Deck
     * .archetypeOverride]'s KDoc).
     */
    @SerialName("strategy_locked") val strategyLocked: Boolean = false,
) : KeysetPageRow

/**
 * DTO for a single card slot within a deck, used by the `upsert_deck_cards` RPC.
 */
@Serializable
data class DeckCardSyncDto(
    @SerialName("scryfall_id") val scryfallId: String,
    val quantity: Int,
    @SerialName("is_sideboard") val isSideboard: Boolean,
    /** Deck Engine Unification plan (D4) — raw [com.mmg.manahub.core.model.DeckCardSource]
     * enum-name. Defaulted `"USER"` for the same server-not-yet-migrated tolerance as
     * [DeckSyncDto.strategyLocked]. */
    val source: String = "USER",
)
