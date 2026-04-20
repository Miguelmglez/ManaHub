package com.mmg.manahub.core.data.remote.collection

import com.mmg.manahub.core.data.local.entity.SyncStatus
import com.mmg.manahub.core.data.local.entity.UserCardEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors the `user_card_collection` Supabase table for serialization. */
@Serializable
data class UserCardCollectionDto(
    val id:                   String?  = null,
    @SerialName("user_id")
    val userId:               String   = "",
    @SerialName("scryfall_id")
    val scryfallId:           String,
    val quantity:             Int      = 1,
    @SerialName("is_foil")
    val isFoil:               Boolean  = false,
    @SerialName("is_alternative_art")
    val isAlternativeArt:     Boolean  = false,
    val condition:            String   = "NM",
    val language:             String   = "en",
    @SerialName("is_for_trade")
    val isForTrade:           Boolean  = false,
    @SerialName("is_in_wishlist")
    val isInWishlist:         Boolean  = false,
    @SerialName("min_trade_value")
    val minTradeValue:        Double?  = null,
    val notes:                String?  = null,
    @SerialName("acquired_at")
    val acquiredAt:           Long?    = null,
    @SerialName("added_at")
    val addedAt:              Long     = 0L,
    @SerialName("updated_at")
    val updatedAt:            String?  = null,
    @SerialName("is_deleted")
    val isDeleted:            Boolean  = false,
)

/** Parameters for the `upsert_user_card` Supabase RPC. */
@Serializable
data class UpsertUserCardParams(
    @SerialName("p_user_id")           val pUserId:          String,
    @SerialName("p_scryfall_id")       val pScryfallId:      String,
    @SerialName("p_quantity")          val pQuantity:        Int,
    @SerialName("p_is_foil")           val pIsFoil:          Boolean,
    @SerialName("p_is_alternative_art") val pIsAlternativeArt: Boolean,
    @SerialName("p_condition")         val pCondition:       String,
    @SerialName("p_language")          val pLanguage:        String,
    @SerialName("p_is_for_trade")      val pIsForTrade:      Boolean,
    @SerialName("p_is_in_wishlist")    val pIsInWishlist:    Boolean,
    @SerialName("p_min_trade_value")   val pMinTradeValue:   Double? = null,
    @SerialName("p_notes")             val pNotes:           String? = null,
    @SerialName("p_acquired_at")       val pAcquiredAt:      Long?   = null,
    @SerialName("p_added_at")          val pAddedAt:         Long,
    @SerialName("p_remote_id")         val pRemoteId:        String? = null,
)

/** Payload for the soft-delete update via Postgrest. */
@Serializable
data class SoftDeletePayload(@SerialName("is_deleted") val isDeleted: Boolean = true)

fun UserCardEntity.toUpsertParams(userId: String) = UpsertUserCardParams(
    pUserId          = userId,
    pScryfallId      = scryfallId,
    pQuantity        = quantity,
    pIsFoil          = isFoil,
    pIsAlternativeArt = isAlternativeArt,
    pCondition       = condition,
    pLanguage        = language,
    pIsForTrade      = isForTrade,
    pIsInWishlist    = isInWishlist,
    pMinTradeValue   = minTradeValue,
    pNotes           = notes,
    pAcquiredAt      = acquiredAt,
    pAddedAt         = addedAt,
    pRemoteId        = remoteId,
)

fun UserCardCollectionDto.toEntity(): UserCardEntity = UserCardEntity(
    scryfallId       = scryfallId,
    quantity         = quantity,
    isFoil           = isFoil,
    isAlternativeArt = isAlternativeArt,
    condition        = condition,
    language         = language,
    isForTrade       = isForTrade,
    isInWishlist     = isInWishlist,
    minTradeValue    = minTradeValue,
    notes            = notes,
    acquiredAt       = acquiredAt,
    addedAt          = addedAt,
    syncStatus       = SyncStatus.SYNCED,
    remoteId         = id,
)
