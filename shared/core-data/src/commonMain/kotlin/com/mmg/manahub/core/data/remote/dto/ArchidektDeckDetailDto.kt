package com.mmg.manahub.core.data.remote.dto

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
    // The Scryfall PRINTING uuid of this exact card (distinct from `oracleCard.uid`, which is
    // the oracle-card uuid shared across all printings) — verified live 2026-07-13, see
    // docs/adr/ADR-004-community-api-contracts.md. Used to build the Scryfall image CDN URL
    // and to resolve the exact printing on tap (no extra Scryfall API call).
    val uid: String = "",
    val edition: ArchidektEditionDto? = null,
    val collectorNumber: String = "",
    val rarity: String = "",
    val prices: ArchidektPricesDto? = null,
)

/** The specific set/printing this card entry belongs to. */
@Serializable
data class ArchidektEditionDto(
    val editioncode: String = "",
    val editionname: String = "",
)

/** Only the two price sources this app consumes; other keys are ignored. */
@Serializable
data class ArchidektPricesDto(
    val tcg: Double? = null,
    val cm: Double? = null,
)

@Serializable
data class ArchidektOracleCardDto(
    val name: String = "",
    val uid: String = "",
    @SerialName("colorIdentity") val colorIdentity: List<String> = emptyList(),
    val manaCost: String = "",
    val types: List<String> = emptyList(),
    val text: String = "",
    val cmc: Double = 0.0,
    val superTypes: List<String> = emptyList(),
    val subTypes: List<String> = emptyList(),
    val layout: String = "normal",
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
