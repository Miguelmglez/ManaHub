package com.mmg.manahub.core.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Response DTOs for the `manahub-competitive` Cloudflare Worker (Competitive feature, Phase 3).
 * Mirrors the Worker's `src/types.ts` contract exactly — see
 * `cloudflare/manahub-competitive/src/types.ts`. Every field is defaulted so the deserializer
 * tolerates missing/unknown keys, matching the [CommunityAggregateResponseDto] family's
 * "tolerant deserialization" convention.
 */
@Serializable
data class MetaSnapshotDto(
    val status: String = "ok",
    val format: String = "",
    val week: String = "",
    val numDecksSampled: Int = 0,
    val archetypes: List<ArchetypeShareDto> = emptyList(),
    val trendingCards: List<TrendingCardDto> = emptyList(),
    val sources: List<SourceStatusDto> = emptyList(),
    val attribution: List<String> = emptyList(),
    val cachedAt: Long = 0,
)

@Serializable
data class ArchetypeShareDto(
    val key: String = "",
    val label: String = "",
    val colors: List<String> = emptyList(),
    val keyCards: List<String> = emptyList(),
    val deckCount: Int = 0,
    val metaSharePct: Double = 0.0,
    val deltaPct: Double? = null,
    val representativeDeck: NormalizedDecklistDto? = null,
)

@Serializable
data class NormalizedDecklistDto(
    val source: String = "",
    val eventName: String = "",
    val eventUrl: String? = null,
    val player: String? = null,
    val format: String = "",
    val result: String? = null,
    val colors: List<String> = emptyList(),
    val cards: List<DecklistCardDto> = emptyList(),
)

@Serializable
data class DecklistCardDto(
    val name: String = "",
    val quantity: Int = 0,
)

@Serializable
data class TrendingCardDto(
    val name: String = "",
    val playRatePct: Double = 0.0,
    val deltaPct: Double? = null,
)

/**
 * Status of one upstream data source for a given [MetaSnapshotDto] fetch. `active = false`
 * with a non-null [reason] (e.g. "TOPDECK_API_KEY not configured...") explains WHY a source
 * contributed nothing this week — this is expected v1 behaviour (MTGO/TopDeck/Spicerack are
 * all in skip-mode until external registrations are done), never an error condition.
 */
@Serializable
data class SourceStatusDto(
    val name: String = "",
    val active: Boolean = false,
    val reason: String? = null,
)

@Serializable
data class LimitedRatingsSnapshotDto(
    val status: String = "ok",
    val source: String = "17lands",
    val attribution: String = "Data: 17Lands.com",
    val expansion: String = "",
    val format: String = "",
    val cards: List<LimitedCardRatingDto> = emptyList(),
    val cachedAt: Long = 0,
)

@Serializable
data class LimitedCardRatingDto(
    val name: String = "",
    val color: String = "",
    val rarity: String = "",
    /** Average Last Seen At (pack position). */
    val avgSeen: Double? = null,
    /** Average Taken At (pick position). */
    val avgPick: Double? = null,
    val playRatePct: Double? = null,
    val gpWinRatePct: Double? = null,
    val ohWinRatePct: Double? = null,
    val gdWinRatePct: Double? = null,
    val gihWinRatePct: Double? = null,
    val iwdPct: Double? = null,
    val sampleSize: Int? = null,
    val imageUrl: String? = null,
)

@Serializable
data class CompetitiveErrorResponseDto(val status: String = "error", val message: String = "Unknown error")
