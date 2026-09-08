package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.cache.CachedCardStrategyTagsEntry
import com.mmg.manahub.core.data.cache.CardStrategyTagsCache
import com.mmg.manahub.core.data.remote.CARD_STRATEGY_TAGS_ORACLE_ID_CHUNK
import com.mmg.manahub.core.data.remote.CardStrategyTagsRemoteDataSourceContract
import com.mmg.manahub.core.data.remote.dto.CardStrategyTagsPayloadDto
import com.mmg.manahub.core.data.remote.dto.CardStrategyTagsRowDto
import com.mmg.manahub.core.data.tagging.TagDictionary
import com.mmg.manahub.core.domain.repository.CardStrategyTagsRepository
import com.mmg.manahub.core.domain.repository.CardStrategyTagsResult
import com.mmg.manahub.core.domain.repository.CardStrategyTagsSubmission
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val strategyTagsJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** 14-day freshness window: precomputed tags change rarely (only on a pipeline re-run, roughly
 *  per new-set release) — a longer TTL than [CommunityAggregateRepositoryImpl]'s 7-day window is
 *  deliberate and safe here. */
private const val CARD_STRATEGY_TAGS_FRESH_MS = 14L * 24 * 60 * 60 * 1000

/**
 * Cache-first implementation of [CardStrategyTagsRepository] (Deck Engine Unification plan, D8,
 * §5 Phase 5c) — mirrors [CommunityAggregateRepositoryImpl]'s layering: Room cache (14-day
 * freshness) -> [remote] (the Supabase `card_strategy_tags` table) -> stale cache as a last resort
 * -> [CardStrategyTagsResult.NotFound]/[CardStrategyTagsResult.Error]. This repository NEVER falls
 * back to on-device analysis itself — that fallback is the CALLER's responsibility (see
 * [com.mmg.manahub.core.domain.usecase.card.RefreshCardStrategyTagsUseCase] and
 * `ResolveCardStrategyTagsUseCase` in `:app`), keeping this class a pure "ask the precomputed
 * source" concern.
 */
class CardStrategyTagsRepositoryImpl(
    private val remote: CardStrategyTagsRemoteDataSourceContract,
    private val cache: CardStrategyTagsCache,
    private val crashReporter: CrashReporter,
    private val dispatcherProvider: DispatcherProvider,
    private val now: () -> Long,
) : CardStrategyTagsRepository {

    override suspend fun getStrategyTags(oracleId: String): CardStrategyTagsResult =
        withContext(dispatcherProvider.io) {
            if (oracleId.isBlank()) return@withContext CardStrategyTagsResult.NotFound

            val cached = cache.get(oracleId)
            if (cached != null && isFresh(cached.fetchedAt)) {
                markSource("cache_fresh")
                return@withContext decode(cached, isStale = false) ?: CardStrategyTagsResult.NotFound
            }

            try {
                val row = remote.getByOracleId(oracleId)
                if (row == null) {
                    // No pipeline row yet for this card (never processed, or genuinely brand new).
                    // A stale cache entry (however old) is still preferable to nothing — the
                    // underlying tags rarely change between pipeline runs.
                    markSource("remote_not_found")
                    return@withContext cached?.let { decode(it, isStale = true) } ?: CardStrategyTagsResult.NotFound
                }
                cache.insert(row.toCacheEntry())
                markSource("remote_found")
                toFound(row.payload, isStale = false)
            } catch (e: Exception) {
                recordFailure(oracleId, e)
                markSource("error_fallback")
                cached?.let { decode(it, isStale = true) } ?: CardStrategyTagsResult.Error("Strategy tags unavailable")
            }
        }

    /**
     * Bulk collection hydration (2026-09-07) — the batched read behind `CardTagHydrationWorker`.
     * Per-id semantics are IDENTICAL to [getStrategyTags]: a fresh cache entry is served with no
     * network work at all, only the remainder is fetched, and every returned row is written to the
     * cache so `CardDao.getScryfallIdsMissingStrategyTags` stays self-terminating. A genuine remote
     * miss still writes NO cache row (unchanged behavior — `ResolveCardStrategyTagsUseCase`'s
     * on-device [submitStrategyTags] write-back is what eventually caches those ids).
     *
     * A chunk that comes back shorter than it was asked for means "those ids have no row", never
     * "stop early": every remaining chunk is still fetched, and a chunk-level failure degrades only
     * that chunk's ids (stale cache if any, otherwise [CardStrategyTagsResult.Error]).
     */
    override suspend fun getStrategyTagsBatch(oracleIds: Set<String>): Map<String, CardStrategyTagsResult> =
        withContext(dispatcherProvider.io) {
            val ids = oracleIds.filter { it.isNotBlank() }
            if (ids.isEmpty()) return@withContext emptyMap()

            val results = mutableMapOf<String, CardStrategyTagsResult>()
            val staleCached = mutableMapOf<String, CachedCardStrategyTagsEntry>()
            val toFetch = mutableListOf<String>()

            ids.forEach { oracleId ->
                val cached = cache.get(oracleId)
                if (cached != null && isFresh(cached.fetchedAt)) {
                    results[oracleId] = decode(cached, isStale = false) ?: CardStrategyTagsResult.NotFound
                } else {
                    cached?.let { staleCached[oracleId] = it }
                    toFetch += oracleId
                }
            }
            if (toFetch.isEmpty()) {
                markSource("batch_cache_fresh")
                return@withContext results
            }

            var anyChunkFailed = false
            toFetch.chunked(CARD_STRATEGY_TAGS_ORACLE_ID_CHUNK).forEach { chunk ->
                try {
                    val rows = remote.getByOracleIds(chunk).associateBy { it.oracleId }
                    chunk.forEach { oracleId ->
                        val row = rows[oracleId]
                        if (row == null) {
                            results[oracleId] = staleCached[oracleId]?.let { decode(it, isStale = true) }
                                ?: CardStrategyTagsResult.NotFound
                        } else {
                            cache.insert(row.toCacheEntry())
                            results[oracleId] = toFound(row.payload, isStale = false)
                        }
                    }
                } catch (e: Exception) {
                    anyChunkFailed = true
                    recordBatchFailure(chunk.size, e)
                    chunk.forEach { oracleId ->
                        results[oracleId] = staleCached[oracleId]?.let { decode(it, isStale = true) }
                            ?: CardStrategyTagsResult.Error("Strategy tags unavailable")
                    }
                }
            }
            markSource(if (anyChunkFailed) "batch_error_fallback" else "batch_remote")
            results
        }

    /**
     * Plan §8a addendum — pushes a device-computed [submission] to `submit_card_strategy_tags`
     * and, on success, ALSO populates the local cache with the just-submitted payload (a real
     * Room-cache-population point, exactly like a fresh remote *read* hit would have done —
     * avoids immediately re-hitting the network for the SAME card again this TTL window). NEVER
     * throws: any failure (offline, unauthenticated, rate-limited, validation-rejected) is an
     * EXPECTED degraded path for a best-effort background contribution, logged at LOG level only
     * (never [CrashReporter.recordException]/a Non-Fatal — this is not a bug, see CLAUDE.md's
     * telemetry conventions).
     */
    override suspend fun submitStrategyTags(oracleId: String, submission: CardStrategyTagsSubmission) {
        if (oracleId.isBlank()) return
        withContext(dispatcherProvider.io) {
            val payload = CardStrategyTagsPayloadDto(
                tags = submission.tags,
                tribes = submission.tribes,
                themes = submission.themes,
                archetypes = submission.archetypes,
                sources = listOf("device"),
            )
            try {
                remote.submit(oracleId, payload)
                cache.insert(
                    CachedCardStrategyTagsEntry(
                        oracleId = oracleId,
                        payloadJson = strategyTagsJson.encodeToString(CardStrategyTagsPayloadDto.serializer(), payload),
                        pipelineVersion = "device",
                        // Not a formatted ISO-8601 instant (no kotlinx-datetime dependency here) —
                        // this field is carried through as opaque provenance metadata only; freshness
                        // is decided by [fetchedAt]/[isFresh], never by parsing this string.
                        generatedAt = now().toString(),
                        fetchedAt = now(),
                    )
                )
            } catch (e: Exception) {
                crashReporter.log("card_strategy_tags_submit_failed")
            }
        }
    }

    private fun isFresh(fetchedAt: Long): Boolean = now() - fetchedAt < CARD_STRATEGY_TAGS_FRESH_MS

    /** Crash-time context / coarse precomputed-vs-fallback signal (RUN 7c telemetry) — O(1) custom-key
     *  overwrite, deliberately not a [crashReporter] log (this resolves on nearly every card
     *  add/search/detail-view cache-miss, so a log breadcrumb here would be excessive volume). */
    private fun markSource(source: String) {
        crashReporter.setCustomKey("card_strategy_tags_source", source)
    }

    private fun decode(entry: CachedCardStrategyTagsEntry, isStale: Boolean): CardStrategyTagsResult? =
        try {
            val payload = strategyTagsJson.decodeFromString(CardStrategyTagsPayloadDto.serializer(), entry.payloadJson)
            toFound(payload, isStale)
        } catch (e: Exception) {
            null
        }

    /** Resolves raw string tag keys to [CardTag]s. [TagDictionary] only holds hand-authored
     *  ARCHETYPE/STRATEGY/ROLE/KEYWORD entries — it is NOT a validity filter for every tag key
     *  the pipeline can emit. [com.mmg.manahub.core.data.tagging.TypeLineAnalyzer] synthesizes
     *  [com.mmg.manahub.core.model.TagCategory.TYPE] tags (card types + creature subtypes, e.g.
     *  "creature"/"artifact"/"elf") directly from the type line and never registers them in the
     *  dictionary, so a dictionary miss on its own does NOT mean "unknown/drifted key" — it is
     *  the expected shape for every TYPE tag. A dictionary hit keeps its authored category;
     *  a miss falls back to [com.mmg.manahub.core.model.TagCategory.TYPE] (the only analyzer that
     *  emits dictionary-less keys today), rather than being dropped. */
    private fun toFound(payload: CardStrategyTagsPayloadDto, isStale: Boolean): CardStrategyTagsResult.Found =
        CardStrategyTagsResult.Found(
            tags    = payload.tags.map { key -> CardTag(key, TagDictionary.get(key)?.category ?: TagCategory.TYPE) },
            tribes  = payload.tribes,
            isStale = isStale,
        )

    private fun CardStrategyTagsRowDto.toCacheEntry(): CachedCardStrategyTagsEntry =
        CachedCardStrategyTagsEntry(
            oracleId        = oracleId,
            payloadJson     = strategyTagsJson.encodeToString(CardStrategyTagsPayloadDto.serializer(), payload),
            pipelineVersion = pipelineVersion,
            generatedAt     = generatedAt,
            fetchedAt       = now(),
        )

    private fun recordBatchFailure(chunkSize: Int, e: Exception) {
        crashReporter.log("card_strategy_tags_batch_fetch_failed")
        crashReporter.recordException(e)
        crashReporter.setCustomKey("card_strategy_tags_batch_size", chunkSize.toString())
    }

    private fun recordFailure(oracleId: String, e: Exception) {
        crashReporter.log("card_strategy_tags_fetch_failed")
        crashReporter.recordException(e)
        crashReporter.setCustomKey("card_strategy_tags_oracle_id_len", oracleId.length.toString())
    }
}
