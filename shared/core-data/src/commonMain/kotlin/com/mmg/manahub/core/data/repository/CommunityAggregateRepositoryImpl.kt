package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.cache.CommunityAggregateCache
import com.mmg.manahub.core.data.remote.CommunityAggregateApiContract
import com.mmg.manahub.core.data.remote.CommunityAggregateKeys
import com.mmg.manahub.core.data.remote.SixtyFallbackFetcher
import com.mmg.manahub.core.data.remote.dto.CommanderAggregateResponseDto
import com.mmg.manahub.core.data.remote.dto.SixtyAggregateResponseDto
import com.mmg.manahub.core.data.remote.mapper.toDomain
import com.mmg.manahub.core.domain.repository.CommunityAggregateRepository
import com.mmg.manahub.core.model.AggregateSource
import com.mmg.manahub.core.model.CommunityAggregate
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.TrendingSnapshot
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val aggregateJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** 7-day freshness window for community aggregate snapshots (Phase 3.2/3.3) — matches the
 * Worker's own KV TTL, distinct from [CachePolicy]'s 24h card-cache tuning. */
private const val AGGREGATE_FRESH_MS = 7L * 24 * 60 * 60 * 1000

/**
 * Cache-first, Worker-backed implementation of [CommunityAggregateRepository] (Phase 3.3).
 *
 * Layering: Room/IndexedDB snapshot cache (7-day freshness) -> [api] (the `manahub-community`
 * Worker) -> [sixtyFallback] (a reduced-sample direct-Archidekt source), degraded-never-dead,
 * **for the 60-card path only**.
 *
 * The Commander path has NO meaningful direct fallback: EDHREC's staple-dampened synergy,
 * average type distribution, and mana curve are aggregated server-side from a much larger
 * sample than this app could ever fetch client-side, and there is no way to reconstruct that
 * shape from a handful of raw Archidekt decks without re-implementing the Worker's entire
 * pipeline on-device. When the Worker is unreachable for the Commander path, this falls back
 * to the last cached snapshot (any age) before finally surfacing an error — it never
 * synthesizes a fake EDHREC-shaped response.
 *
 * Per ADR-004 §5, on the web target Archidekt's API has no CORS grant for arbitrary origins,
 * so [sixtyFallback] is Android-reachable only in practice; on wasmJs it fails the same way an
 * unreachable Worker would (caught, surfaced as an error, never a hard crash).
 *
 * [api] and [sixtyFallback] are narrow interfaces (not the concrete Ktor/Archidekt classes) so
 * this class's own cache/Worker/fallback DISPATCH logic is unit-testable with trivial fakes —
 * see `CommunityAggregateRepositoryImplTest` (commonTest, no Ktor engine mock needed).
 *
 * @param isEngineEnabled reads the D4 `communityEngineEnabledFlow` feature flag (platform
 *   DataStore behind a lambda so this stays commonMain-clean); every method short-circuits to
 *   [DataResult.Error] with no network/cache access at all when this returns false.
 */
class CommunityAggregateRepositoryImpl(
    private val api: CommunityAggregateApiContract,
    private val cache: CommunityAggregateCache,
    private val sixtyFallback: SixtyFallbackFetcher,
    private val crashReporter: CrashReporter,
    private val dispatcherProvider: DispatcherProvider,
    private val now: () -> Long,
    private val isEngineEnabled: suspend () -> Boolean,
) : CommunityAggregateRepository {

    override suspend fun getCommanderAggregate(commanderName: String): DataResult<CommunityAggregate.Commander> =
        withContext(dispatcherProvider.io) {
            if (!isEngineEnabled()) return@withContext disabledError()

            val key = CommunityAggregateKeys.commanderCacheKey(commanderName)
            val cached = cache.get(key)
            if (cached != null && isFresh(cached.cachedAt)) {
                val decoded = decodeCommander(cached.json, AggregateSource.CACHE)
                return@withContext if (decoded != null) DataResult.Success(decoded) else DataResult.Error("Corrupt community cache entry")
            }

            try {
                val dto = api.getCommanderAggregate(commanderName)
                cache.insert(key, aggregateJson.encodeToString(CommanderAggregateResponseDto.serializer(), dto), now())
                DataResult.Success(dto.toDomain(AggregateSource.WORKER))
            } catch (e: Exception) {
                recordFailure("community_commander_aggregate", commanderName, e)
                val stale = cached?.let { decodeCommander(it.json, AggregateSource.CACHE) }
                if (stale != null) DataResult.Success(stale, isStale = true) else DataResult.Error("Community data unavailable")
            }
        }

    override suspend fun getSixtyAggregate(
        signatureCards: List<String>,
        format: Int,
    ): DataResult<CommunityAggregate.Sixty> = withContext(dispatcherProvider.io) {
        if (!isEngineEnabled()) return@withContext disabledError()
        if (signatureCards.isEmpty()) return@withContext DataResult.Error("At least one signature card is required")

        val key = CommunityAggregateKeys.sixtyCacheKey(format, signatureCards)
        val cached = cache.get(key)
        if (cached != null && isFresh(cached.cachedAt)) {
            val decoded = decodeSixty(cached.json, AggregateSource.CACHE)
            return@withContext if (decoded != null) DataResult.Success(decoded) else DataResult.Error("Corrupt community cache entry")
        }

        try {
            val dto = api.getSixtyAggregate(signatureCards, format)
            if (dto.status != "building") {
                cache.insert(key, aggregateJson.encodeToString(SixtyAggregateResponseDto.serializer(), dto), now())
            }
            DataResult.Success(dto.toDomain(AggregateSource.WORKER))
        } catch (e: Exception) {
            recordFailure("community_sixty_aggregate", signatureCards.joinToString(","), e)
            val stale = cached?.let { decodeSixty(it.json, AggregateSource.CACHE) }
            when {
                stale != null -> DataResult.Success(stale, isStale = true)
                else -> {
                    val fallback = sixtyFallback.fetch(signatureCards.first(), format)
                    if (fallback != null) DataResult.Success(fallback) else DataResult.Error("Community data unavailable")
                }
            }
        }
    }

    override suspend fun getSimilarDecks(commanderName: String, limit: Int): DataResult<List<String>> =
        withContext(dispatcherProvider.io) {
            if (!isEngineEnabled()) return@withContext disabledError()
            try {
                DataResult.Success(api.getSimilar(commanderName, limit).similar)
            } catch (e: Exception) {
                recordFailure("community_similar_decks", commanderName, e)
                DataResult.Error("Community data unavailable")
            }
        }

    override suspend fun getTrending(week: String?): DataResult<TrendingSnapshot> =
        withContext(dispatcherProvider.io) {
            if (!isEngineEnabled()) return@withContext disabledError()
            try {
                DataResult.Success(api.getTrending(week).toDomain())
            } catch (e: Exception) {
                // Trending is a nice-to-have widget signal — never noisy in Crashlytics.
                crashReporter.log("community_trending_fetch_failed")
                DataResult.Error("Trending data unavailable")
            }
        }

    private fun isFresh(cachedAt: Long): Boolean = now() - cachedAt < AGGREGATE_FRESH_MS

    private fun decodeCommander(json: String, source: AggregateSource): CommunityAggregate.Commander? =
        try {
            aggregateJson.decodeFromString(CommanderAggregateResponseDto.serializer(), json).toDomain(source)
        } catch (e: Exception) {
            null
        }

    private fun decodeSixty(json: String, source: AggregateSource): CommunityAggregate.Sixty? =
        try {
            aggregateJson.decodeFromString(SixtyAggregateResponseDto.serializer(), json).toDomain(source)
        } catch (e: Exception) {
            null
        }

    private fun <T> disabledError(): DataResult<T> = DataResult.Error("Community engine is disabled")

    private fun recordFailure(tag: String, context: String, e: Exception) {
        crashReporter.log(tag)
        crashReporter.recordException(e)
        val statusCode = (e as? ResponseException)?.response?.status?.value
        if (statusCode != null) crashReporter.setCustomKey("${tag}_http_code", statusCode.toString())
        crashReporter.setCustomKey("${tag}_context_len", context.length.toString())
    }
}
