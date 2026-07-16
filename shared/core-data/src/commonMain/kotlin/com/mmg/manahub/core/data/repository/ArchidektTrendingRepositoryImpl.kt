package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.cache.CommunityAggregateCache
import com.mmg.manahub.core.data.remote.ArchidektClient
import com.mmg.manahub.core.data.remote.dto.ArchidektSearchResultDto
import com.mmg.manahub.core.domain.repository.ArchidektTrendingRepository
import com.mmg.manahub.core.model.ArchidektFormat
import com.mmg.manahub.core.model.ArchidektTrendingDeck
import com.mmg.manahub.core.model.CommunityDeckSearchFilters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val archidektTrendingJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

private const val ARCHIDEKT_TRENDING_CACHE_KEY = "home_archidekt_trending"

/** 18-hour freshness window, within the plan's 12-24h TTL guidance. */
private const val ARCHIDEKT_TRENDING_FRESH_MS = 18L * 60 * 60 * 1000

/** Number of slides surfaced on the Home dashboard. */
private const val MAX_TRENDING_SLIDES = 3

private const val ARCHIDEKT_DECK_URL_PREFIX = "https://archidekt.com/decks/"

/**
 * Cache-first [ArchidektTrendingRepository] backed by the EXISTING [ArchidektClient] (Home
 * feature overhaul Phase 1.2.c) — reuses the same Ktor client + DTOs the Community Decks feature
 * already ships (`ArchidektSearchResultDto`, verified live against `GET /api/decks/v3/` with
 * `deckFormat=3` — confirmed empirically to filter to Commander decks).
 *
 * Archidekt's `v3` search endpoint exposes no "likes"/favorites or weekly-delta signal, only an
 * all-time [com.mmg.manahub.core.data.remote.dto.ArchidektDeckSummaryDto.viewCount] — decks are
 * ranked by `orderBy=-viewCount`, so the UI must present this as "Popular on Archidekt", never
 * "Trending this week".
 *
 * Caches the raw search-result JSON via the shared [CommunityAggregateCache] key-value
 * abstraction (Room on Android) with an 18h TTL; a failed fetch falls back to the cached payload
 * or an empty list — [observeTrendingDecks] never surfaces an inline error, and the Home SOCIAL_HUB
 * widget simply omits the slides when the list is empty (no-stub rule, "explicitly-labeled
 * shortcut" carve-out does not apply here — this slide is omitted entirely rather than shown empty).
 * A cache entry that fails to decode (corrupted JSON) is treated as a cache miss regardless of its
 * TTL freshness, so the flow always emits at least an empty list rather than staying silent.
 */
class ArchidektTrendingRepositoryImpl(
    private val client: ArchidektClient,
    private val cache: CommunityAggregateCache,
    private val crashReporter: CrashReporter,
    private val dispatcherProvider: DispatcherProvider,
    private val now: () -> Long,
) : ArchidektTrendingRepository {

    override fun observeTrendingDecks(): Flow<List<ArchidektTrendingDeck>> = flow {
        val cached = cache.get(ARCHIDEKT_TRENDING_CACHE_KEY)
        val cachedDto = cached?.let { decode(it.json) }
        if (cachedDto != null) emit(cachedDto.toTrendingDecks())

        // A present-but-undecodable cache entry (e.g. after a non-backward-compatible DTO change
        // ships) must be treated the same as "no cache" for staleness purposes. Without the
        // `cachedDto == null` check, a corrupted entry that is still within the TTL window would
        // short-circuit via `if (!isStale) return@flow` having emitted NOTHING at all -- silently
        // stalling any collector with no other fallback for this source (e.g. Home's
        // socialExtrasFlow combine) until the entry naturally expires, up to 18h later.
        val isStale = cached == null || cachedDto == null || (now() - cached.cachedAt) > ARCHIDEKT_TRENDING_FRESH_MS
        if (!isStale) return@flow

        val fresh = fetchFresh()
        when {
            fresh != null -> emit(fresh.toTrendingDecks())
            cachedDto == null -> emit(emptyList())
        }
    }.catch {
        crashReporter.log("home_archidekt_trending_flow_error")
        emit(emptyList())
    }

    private suspend fun fetchFresh(): ArchidektSearchResultDto? = withContext(dispatcherProvider.io) {
        runCatching {
            val dto = client.searchDecks(
                CommunityDeckSearchFilters(
                    deckFormatId = ArchidektFormat.COMMANDER.apiId,
                    orderBy = "-viewCount",
                    pageSize = 20,
                ),
            )
            runCatching {
                cache.insert(
                    key = ARCHIDEKT_TRENDING_CACHE_KEY,
                    json = archidektTrendingJson.encodeToString(ArchidektSearchResultDto.serializer(), dto),
                    cachedAt = now(),
                )
            }
            dto
        }.getOrElse {
            crashReporter.log("home_archidekt_trending_fetch_failed")
            null
        }
    }

    private fun decode(json: String): ArchidektSearchResultDto? =
        runCatching { archidektTrendingJson.decodeFromString(ArchidektSearchResultDto.serializer(), json) }.getOrNull()

    private fun ArchidektSearchResultDto.toTrendingDecks(): List<ArchidektTrendingDeck> =
        results.asSequence()
            .filterNot { it.private || it.unlisted || it.theorycrafted }
            .filter { it.name.isNotBlank() }
            .take(MAX_TRENDING_SLIDES)
            .map { deck ->
                ArchidektTrendingDeck(
                    id = deck.id,
                    name = deck.name,
                    viewCount = deck.viewCount,
                    deckUrl = "$ARCHIDEKT_DECK_URL_PREFIX${deck.id}",
                )
            }
            .toList()
}
