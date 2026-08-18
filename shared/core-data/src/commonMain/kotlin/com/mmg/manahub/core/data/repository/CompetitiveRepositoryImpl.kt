package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.cache.CompetitiveLimitedRatingsCache
import com.mmg.manahub.core.data.cache.CompetitiveMetaCache
import com.mmg.manahub.core.data.network.CompetitiveRequestQueue
import com.mmg.manahub.core.data.remote.CompetitiveApiContract
import com.mmg.manahub.core.data.remote.dto.LimitedRatingsSnapshotDto
import com.mmg.manahub.core.data.remote.dto.MetaSnapshotDto
import com.mmg.manahub.core.data.remote.mapper.toDomain
import com.mmg.manahub.core.domain.repository.CompetitiveRepository
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.LimitedRatingsSnapshot
import com.mmg.manahub.core.model.MetaSnapshot
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val competitiveJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** 24h freshness window for competitive snapshots (Phase 3) — the Worker's own weekly-cron/KV
 * cadence means a snapshot is rarely fresher than this anyway, and matches the daily cadence
 * other 24h-tuned caches in this codebase use (e.g. price refresh, per ADR-005). */
private const val COMPETITIVE_FRESH_MS = 24L * 60 * 60 * 1000

/**
 * Cache-first, Worker-backed implementation of [CompetitiveRepository] (Competitive feature,
 * Phase 3).
 *
 * Layering: Room/IndexedDB snapshot cache (24h freshness) -> [api] (the `manahub-competitive`
 * Worker), degraded-never-dead — a Worker failure with a stale cache present serves the stale
 * snapshot flagged [DataResult.Success.isStale] rather than erroring; only a Worker failure with
 * NO cache at all yields [DataResult.Error]. This mirrors
 * [com.mmg.manahub.core.data.repository.CommunityAggregateRepositoryImpl]'s layering exactly —
 * see that class's KDoc for the fuller rationale of this pattern.
 *
 * [metaCache] and [limitedRatingsCache] are narrow `commonMain` interfaces (NOT the concrete
 * Room DAOs `CompetitiveMetaCacheDao`/`CompetitiveLimitedRatingsCacheDao`, which live in the
 * `:app` module and use `androidx.room` — neither can be referenced from this `commonMain`
 * class). An Android-side adapter implementing these interfaces (wrapping the Room DAOs, mirroring
 * `CommunityAggregateCacheImpl`) is wired via Koin in a later phase; a web adapter follows the
 * same interface with an IndexedDB/no-op backing.
 *
 * @param isEngineEnabled reads the `competitiveEnabledFlow` feature flag (platform DataStore
 *   behind a lambda so this stays commonMain-clean, same pattern as
 *   [CommunityAggregateRepositoryImpl]); every method short-circuits to [DataResult.Error] with
 *   no network/cache access at all when this returns false.
 */
class CompetitiveRepositoryImpl(
    private val api: CompetitiveApiContract,
    private val requestQueue: CompetitiveRequestQueue,
    private val metaCache: CompetitiveMetaCache,
    private val limitedRatingsCache: CompetitiveLimitedRatingsCache,
    private val crashReporter: CrashReporter,
    private val dispatcherProvider: DispatcherProvider,
    private val now: () -> Long,
    private val isEngineEnabled: suspend () -> Boolean,
) : CompetitiveRepository {

    override suspend fun getWeeklyMeta(format: String): DataResult<MetaSnapshot> =
        withContext(dispatcherProvider.io) {
            if (!isEngineEnabled()) return@withContext disabledError()

            val cached = metaCache.get(format)
            if (cached != null && isFresh(cached.cachedAt)) {
                val decoded = decodeMeta(cached.json)
                return@withContext if (decoded != null) {
                    DataResult.Success(decoded)
                } else {
                    DataResult.Error("Corrupt competitive meta cache entry")
                }
            }

            try {
                val dto = requestQueue.execute { api.getWeeklyMeta(format) }
                metaCache.insert(format, competitiveJson.encodeToString(MetaSnapshotDto.serializer(), dto), now())
                // An empty `archetypes` list is a valid v1 state (all archetype sources still in
                // skip-mode) -- it maps straight through to a Success, never an Error.
                DataResult.Success(dto.toDomain())
            } catch (e: Exception) {
                recordFailure("competitive_meta_weekly", format, e)
                val stale = cached?.let { decodeMeta(it.json) }
                crashReporter.setCustomKey("competitive_meta_weekly_stale_fallback", (stale != null).toString())
                if (stale != null) DataResult.Success(stale, isStale = true) else DataResult.Error("Competitive meta data unavailable")
            }
        }

    override suspend fun getLimitedRatings(setCode: String, format: String): DataResult<LimitedRatingsSnapshot> =
        withContext(dispatcherProvider.io) {
            if (!isEngineEnabled()) return@withContext disabledError()

            // Cache key is `setCode` alone -- see CompetitiveLimitedRatingsCache's KDoc: the
            // true upstream identity is (setCode, format), but v1 only ever queries
            // PremierDraft, so this cache holds one snapshot per set regardless of `format`.
            val cached = limitedRatingsCache.get(setCode)
            if (cached != null && isFresh(cached.cachedAt)) {
                val decoded = decodeLimited(cached.json)
                return@withContext if (decoded != null) {
                    DataResult.Success(decoded)
                } else {
                    DataResult.Error("Corrupt competitive limited-ratings cache entry")
                }
            }

            try {
                val dto = requestQueue.execute { api.getLimitedRatings(setCode, format) }
                limitedRatingsCache.insert(setCode, competitiveJson.encodeToString(LimitedRatingsSnapshotDto.serializer(), dto), now())
                DataResult.Success(dto.toDomain())
            } catch (e: Exception) {
                // 404 is expected/common here -- `setCode` is a free-text field with zero
                // validation, so a typo'd set code is user input, not a bug (see recordFailure's
                // benignHttpCodes KDoc).
                recordFailure("competitive_limited_ratings", setCode, e, benignHttpCodes = setOf(HTTP_NOT_FOUND))
                val stale = cached?.let { decodeLimited(it.json) }
                crashReporter.setCustomKey("competitive_limited_ratings_stale_fallback", (stale != null).toString())
                if (stale != null) DataResult.Success(stale, isStale = true) else DataResult.Error("Limited ratings data unavailable")
            }
        }

    private fun isFresh(cachedAt: Long): Boolean = now() - cachedAt < COMPETITIVE_FRESH_MS

    private fun decodeMeta(json: String): MetaSnapshot? =
        try {
            competitiveJson.decodeFromString(MetaSnapshotDto.serializer(), json).toDomain()
        } catch (e: Exception) {
            // Hit on BOTH the fresh-cache-corrupt path and the stale-fallback-decode path -- a
            // single event covers both, the caller doesn't need to distinguish them.
            crashReporter.log("competitive_meta_weekly_cache_corrupt")
            crashReporter.recordException(e)
            null
        }

    private fun decodeLimited(json: String): LimitedRatingsSnapshot? =
        try {
            competitiveJson.decodeFromString(LimitedRatingsSnapshotDto.serializer(), json).toDomain()
        } catch (e: Exception) {
            crashReporter.log("competitive_limited_ratings_cache_corrupt")
            crashReporter.recordException(e)
            null
        }

    private fun <T> disabledError(): DataResult<T> = DataResult.Error("Competitive engine is disabled")

    /**
     * Records a request failure. [benignHttpCodes] lets a call site downgrade specific, expected
     * HTTP statuses to log-only (no [CrashReporter.recordException]) -- e.g. a 404 from a
     * free-text, unvalidated field is normal user input, not a bug, and would otherwise flood
     * Crashlytics non-fatals with false positives (same precedent as the
     * `commander_spellbook_find_combos_failed` downgrade).
     */
    private fun recordFailure(tag: String, context: String, e: Exception, benignHttpCodes: Set<Int> = emptySet()) {
        val statusCode = (e as? ResponseException)?.response?.status?.value
        val isBenign = statusCode != null && statusCode in benignHttpCodes
        crashReporter.log(tag)
        if (!isBenign) crashReporter.recordException(e)
        if (statusCode != null) crashReporter.setCustomKey("${tag}_http_code", statusCode.toString())
        crashReporter.setCustomKey("${tag}_context_len", context.length.toString())
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
