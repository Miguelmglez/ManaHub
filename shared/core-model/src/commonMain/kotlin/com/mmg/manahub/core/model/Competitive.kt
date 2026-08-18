package com.mmg.manahub.core.model

/**
 * Domain models for the `manahub-competitive` Cloudflare Worker (Competitive feature, Phase 3):
 * weekly constructed-format metagame snapshots and 17lands Limited card ratings. See
 * `cloudflare/manahub-competitive/src/types.ts` for the upstream contract this is derived from.
 */

/**
 * Status of one upstream data source that feeds a [MetaSnapshot]. `active = false` with a
 * non-null [reason] tells the UI WHY a source contributed nothing this week (e.g. an API key
 * not yet provisioned) so it can render an honest "still onboarding this source" state instead
 * of looking broken — this is expected v1 behaviour, never an error condition.
 */
data class SourceStatus(
    val name: String,
    val active: Boolean,
    val reason: String?,
)

/** One card entry within a [RepresentativeDeck]'s decklist. */
data class DecklistCard(
    val name: String,
    val quantity: Int,
)

/** One tournament/league decklist representative of an [ArchetypeShare], for the netdeck-import CTA. */
data class RepresentativeDeck(
    val source: String,
    val eventName: String,
    val eventUrl: String?,
    val player: String?,
    val format: String,
    val result: String?,
    val colors: List<String>,
    val cards: List<DecklistCard>,
)

/** One archetype's share of the sampled meta for a given week. */
data class ArchetypeShare(
    val key: String,
    val label: String,
    val colors: List<String>,
    val keyCards: List<String>,
    val deckCount: Int,
    val metaSharePct: Double,
    /** Percentage-point change vs. the previous ISO week, null if no prior data exists. */
    val deltaPct: Double?,
    val representativeDeck: RepresentativeDeck?,
)

/** One card's play-rate trend across the sampled meta for a given week. */
data class TrendingCard(
    val name: String,
    val playRatePct: Double,
    val deltaPct: Double?,
)

/**
 * A weekly metagame snapshot for one constructed format (standard/modern/pioneer/legacy/
 * vintage/pauper).
 *
 * [archetypes] is very likely EMPTY in v1: MTGO/TopDeck/Spicerack sources are all in skip-mode
 * until external registrations are complete — this is a valid "no data yet" state, NOT an
 * error. Callers must render [sources] (which explains why each source is inactive) rather than
 * treating an empty [archetypes] list as a failure.
 */
data class MetaSnapshot(
    val format: String,
    val week: String,
    val numDecksSampled: Int,
    val archetypes: List<ArchetypeShare>,
    val trendingCards: List<TrendingCard>,
    val sources: List<SourceStatus>,
    val attribution: List<String>,
    val cachedAt: Long,
)

/** One card's 17lands Limited draft-format rating. */
data class LimitedCardRating(
    val name: String,
    val color: String,
    val rarity: String,
    /** Average Last Seen At (pack position). */
    val avgSeen: Double?,
    /** Average Taken At (pick position). */
    val avgPick: Double?,
    val playRatePct: Double?,
    val gpWinRatePct: Double?,
    val ohWinRatePct: Double?,
    val gdWinRatePct: Double?,
    val gihWinRatePct: Double?,
    val iwdPct: Double?,
    val sampleSize: Int?,
    val imageUrl: String?,
)

/** 17lands Limited card-ratings snapshot for one expansion + draft format (e.g. PremierDraft). */
data class LimitedRatingsSnapshot(
    val expansion: String,
    val format: String,
    val cards: List<LimitedCardRating>,
    val attribution: String,
    val cachedAt: Long,
)
