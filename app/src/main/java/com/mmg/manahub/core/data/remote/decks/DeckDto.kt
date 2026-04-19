package com.mmg.manahub.core.data.remote.decks

import com.mmg.manahub.core.data.local.entity.DeckEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class DeckDto(
    val id: String? = null,
    @SerialName("local_id")    val localId:     Long    = 0,
    @SerialName("user_id")     val userId:      String  = "",
    val name:                                   String  = "",
    val description:                            String? = null,
    val format:                                 String  = "casual",
    @SerialName("cover_card_id") val coverCardId: String? = null,
    @SerialName("created_at")  val createdAt:   String? = null,
    @SerialName("updated_at")  val updatedAt:   String? = null,
    @SerialName("is_deleted")  val isDeleted:   Boolean = false,
)

@Serializable
data class UpsertDeckParams(
    @SerialName("p_local_id")      val pLocalId:     Long,
    @SerialName("p_name")          val pName:        String,
    @SerialName("p_description")   val pDescription: String? = null,
    @SerialName("p_format")        val pFormat:      String,
    @SerialName("p_cover_card_id") val pCoverCardId: String? = null,
    @SerialName("p_created_at")    val pCreatedAt:   String,
    @SerialName("p_updated_at")    val pUpdatedAt:   String,
)

@Serializable
data class DeckCardDto(
    @SerialName("scryfall_id") val scryfallId:  String,
    val quantity:                               Int,
    @SerialName("is_sideboard") val isSideboard: Boolean,
)

fun DeckEntity.toUpsertParams() = UpsertDeckParams(
    pLocalId     = id,
    pName        = name,
    pDescription = description,
    pFormat      = format,
    pCoverCardId = coverCardId,
    pCreatedAt   = Instant.ofEpochMilli(createdAt).toString(),
    pUpdatedAt   = Instant.ofEpochMilli(updatedAt).toString(),
)
