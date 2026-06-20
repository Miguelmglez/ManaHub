package com.mmg.manahub.feature.communitydecks.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Full deck-detail response from `GET /api/decks/{id}/`.
 *
 * Every field is defaulted so the deserializer tolerates missing/unknown keys
 * (the Json instance is configured with `ignoreUnknownKeys = true` and
 * `coerceInputValues = true`). [deckFormat] defaults to `7` (Custom) to mirror
 * Archidekt's own "unspecified" bucket.
 */
@Serializable
data class ArchidektDeckDetailDto(
    val id: Int,
    val name: String = "",
    val description: String = "",
    @SerialName("deckFormat") val deckFormat: Int = 7,
    val owner: ArchidektOwnerDto? = null,
    val viewCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val cards: List<ArchidektCardEntryDto> = emptyList(),
    val categories: List<ArchidektCategoryDto> = emptyList(),
)

@Serializable
data class ArchidektCardEntryDto(
    val quantity: Int = 1,
    val categories: List<String>? = null,
    val card: ArchidektCardDto? = null,
)

@Serializable
data class ArchidektCardDto(
    val oracleCard: ArchidektOracleCardDto? = null,
    val uid: String = "",
)

@Serializable
data class ArchidektOracleCardDto(
    val name: String = "",
    val uid: String = "",
    @SerialName("colorIdentity") val colorIdentity: List<String> = emptyList(),
    val manaCost: String = "",
    val types: List<String> = emptyList(),
    val text: String = "",
)

@Serializable
data class ArchidektOwnerDto(
    val id: Int = 0,
    val username: String = "",
    val avatar: String = "",
)

@Serializable
data class ArchidektCategoryDto(
    val name: String = "",
    val isPremier: Boolean = false,
    val includedInDeck: Boolean = true,
)
