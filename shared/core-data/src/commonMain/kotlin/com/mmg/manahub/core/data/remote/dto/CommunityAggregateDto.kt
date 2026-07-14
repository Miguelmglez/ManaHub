package com.mmg.manahub.core.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Response DTOs for the `manahub-community` Cloudflare Worker (Phase 3.3). Mirrors the
 * Worker's `src/types.ts` normalized snapshot contract exactly — see
 * `cloudflare/manahub-community/src/types.ts` and
 * `docs/adr/ADR-004-community-api-contracts.md`. Every field is defaulted so the
 * deserializer tolerates missing/unknown keys.
 */
@Serializable
data class CommanderAggregateResponseDto(
    val status: String = "materialized",
    val commander: String = "",
    val numDecksSampled: Int = 0,
    val avgTypeDistribution: AvgTypeDistributionDto = AvgTypeDistributionDto(),
    val manaCurve: Map<String, Int> = emptyMap(),
    val themeTags: List<NamedCountDto> = emptyList(),
    val similarCommanders: List<String> = emptyList(),
    val gameChangersCount: Int = 0,
    val cards: List<AggregateCardEntryDto> = emptyList(),
    val cachedAt: Long = 0,
)

@Serializable
data class AvgTypeDistributionDto(
    val creature: Int = 0,
    val instant: Int = 0,
    val sorcery: Int = 0,
    val artifact: Int = 0,
    val enchantment: Int = 0,
    val battle: Int = 0,
    val planeswalker: Int = 0,
    val land: Int = 0,
)

@Serializable
data class AggregateCardEntryDto(
    val name: String = "",
    val scryfallUid: String? = null,
    val inclusionPct: Float = 0f,
    val synergy: Float = 0f,
    val category: String = "",
    val numDecks: Int = 0,
)

@Serializable
data class SixtyAggregateResponseDto(
    val status: String = "building",
    val canonicalKey: String = "",
    val format: Int = 0,
    val numDecksSampled: Int = 0,
    val deckSummaries: List<SixtyDeckSummaryDto> = emptyList(),
    val colorProfile: Map<String, Int> = emptyMap(),
    val cards: List<AggregateCardEntryDto> = emptyList(),
    val cachedAt: Long = 0,
    val progress: BuildProgressDto? = null,
)

@Serializable
data class SixtyDeckSummaryDto(
    val id: Int = 0,
    val name: String = "",
    val owner: String = "",
    val viewCount: Int = 0,
    val colors: Map<String, Int> = emptyMap(),
    val edhBracket: Int? = null,
)

@Serializable
data class BuildProgressDto(val collected: Int = 0, val target: Int = 0)

@Serializable
data class SimilarDecksResponseDto(
    val status: String = "ok",
    val commander: String = "",
    val similar: List<String> = emptyList(),
)

@Serializable
data class TrendingResponseDto(
    val status: String = "ok",
    val week: String = "",
    val topCommanders: List<NamedCountDto> = emptyList(),
    val topCards: List<NamedCountDto> = emptyList(),
)

@Serializable
data class NamedCountDto(val name: String = "", val count: Int = 0)

@Serializable
data class WorkerErrorResponseDto(val status: String = "error", val message: String = "Unknown error")
