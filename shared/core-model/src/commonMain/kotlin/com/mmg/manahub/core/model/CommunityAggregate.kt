package com.mmg.manahub.core.model

/**
 * Where a [CommunityAggregate] snapshot ultimately came from, for this fetch. The UI uses
 * this (not [DataResult.Success.isStale] alone) to decide whether to show a degradation
 * notice: [WORKER] and [CACHE] are both full-quality data, [DIRECT_FALLBACK] is a reduced
 * client-side sample used only when the Worker itself is unreachable (Phase 3.3 layering:
 * Room cache -> Worker -> direct-fallback, "degraded, never dead").
 */
enum class AggregateSource { WORKER, CACHE, DIRECT_FALLBACK }

/** One card's aggregate standing within a community snapshot (Commander or 60-card). */
data class AggregateCardEntry(
    val name: String,
    val scryfallUid: String?,
    /** 0f..1f fraction of sampled decks including this card. */
    val inclusionPct: Float,
    /** Staple-dampened synergy — EDHREC-native for Commander, computed for 60-card. */
    val synergy: Float,
    val category: String,
    val numDecks: Int,
)

data class AvgTypeDistribution(
    val creature: Int,
    val instant: Int,
    val sorcery: Int,
    val artifact: Int,
    val enchantment: Int,
    val battle: Int,
    val planeswalker: Int,
    val land: Int,
)

data class NamedCount(val name: String, val count: Int)

data class SixtyDeckSummary(
    val archidektId: Int,
    val name: String,
    val owner: String,
    val viewCount: Int,
    val colors: Map<String, Int>,
    val edhBracket: Int?,
)

/**
 * A community aggregate snapshot for either format family. See
 * `docs/claude-code-prompt-deck-doctor-community.md` Phase 3 and
 * `docs/adr/ADR-004-community-api-contracts.md` for the upstream contract this is derived
 * from.
 */
sealed class CommunityAggregate {

    data class Commander(
        val commander: String,
        val numDecksSampled: Int,
        val avgTypeDistribution: AvgTypeDistribution,
        val manaCurve: Map<String, Int>,
        val themeTags: List<NamedCount>,
        val similarCommanders: List<String>,
        val gameChangersCount: Int,
        val cards: List<AggregateCardEntry>,
        val cachedAt: Long,
        val source: AggregateSource,
    ) : CommunityAggregate()

    sealed class Sixty : CommunityAggregate() {

        abstract val canonicalKey: String

        data class Materialized(
            override val canonicalKey: String,
            val format: Int,
            val numDecksSampled: Int,
            val deckSummaries: List<SixtyDeckSummary>,
            val colorProfile: Map<String, Int>,
            val cards: List<AggregateCardEntry>,
            val cachedAt: Long,
            val source: AggregateSource,
        ) : Sixty()

        /**
         * The Worker is still collecting community decks for this canonical key (Phase 3.2's
         * incremental build). The caller should re-poll with backoff — this is NOT an error.
         */
        data class Building(
            override val canonicalKey: String,
            val collected: Int,
            val target: Int,
        ) : Sixty()
    }
}

data class TrendingSnapshot(
    val week: String,
    val topCommanders: List<NamedCount>,
    val topCards: List<NamedCount>,
)
