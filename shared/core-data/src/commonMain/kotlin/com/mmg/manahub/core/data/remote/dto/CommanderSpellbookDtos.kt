package com.mmg.manahub.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request/response DTOs for Commander Spellbook's `POST /find-my-combos` (Deck Engine
 * Unification plan D7, Phase 4.3). Schema verified live 2026-07-20 against
 * `https://backend.commanderspellbook.com/schema/` (OpenAPI). The API uses camelCase JSON field
 * names throughout. Only the fields this app actually consumes are declared -- every DTO relies
 * on `ignoreUnknownKeys = true` (see `CommanderSpellbookKoinModule`'s Ktor client config) so the
 * upstream schema can grow additive fields without breaking parsing (unofficial-adjacent third
 * party -- defensive parsing throughout, per plan D7).
 */

@Serializable
data class CardInDeckRequestDto(
    val card: String,
    val quantity: Int = 1,
)

@Serializable
data class FindMyCombosRequestDto(
    val main: List<CardInDeckRequestDto> = emptyList(),
    val commanders: List<CardInDeckRequestDto> = emptyList(),
)

/**
 * The API wraps its single aggregate result object in a DRF-paginator-shaped envelope
 * (`count`/`next`/`previous`/`results`) even though a `find-my-combos` call is not itself
 * paginated -- `results` holds ONE [FindMyCombosResultDto], not a list. Nullable/defaulted
 * throughout so an unexpected envelope shape degrades to "no combos found" rather than a parse
 * failure.
 */
@Serializable
data class FindMyCombosResponseDto(
    val count: Int? = null,
    val results: FindMyCombosResultDto? = null,
)

@Serializable
data class FindMyCombosResultDto(
    val identity: String? = null,
    val included: List<VariantDto> = emptyList(),
    val almostIncluded: List<VariantDto> = emptyList(),
)

@Serializable
data class VariantDto(
    val id: String,
    val status: String? = null,
    val description: String = "",
    val uses: List<CardInVariantDto> = emptyList(),
    val produces: List<FeatureProducedByVariantDto> = emptyList(),
)

@Serializable
data class CardInVariantDto(
    val card: CardRefDto,
    val quantity: Int = 1,
    @SerialName("mustBeCommander") val mustBeCommander: Boolean = false,
)

@Serializable
data class CardRefDto(
    val name: String,
)

@Serializable
data class FeatureProducedByVariantDto(
    val feature: FeatureRefDto,
    val quantity: Int = 1,
)

@Serializable
data class FeatureRefDto(
    val name: String,
)
