package com.mmg.manahub.core.domain.usecase.card

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.CardTagsUpdate
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.core.data.local.mapper.toSuggestedTagsJson
import com.mmg.manahub.core.data.local.mapper.toTagsJson
import com.mmg.manahub.core.domain.repository.CardStrategyTagsRepository
import com.mmg.manahub.core.domain.repository.CardStrategyTagsResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Bulk strategy-tag hydration for the user's own cards (2026-09-07) — the batched replacement for
 * the 40-cards-a-day drip that left a 1300-card collection ~33 days from full coverage (and every
 * Collection TAG group reading "Untagged" in the meantime).
 *
 * ## Candidates
 * Two queries, with different jobs:
 * - [CardDao.getScryfallIdsMissingStrategyTags] — the self-terminating primary source. "Never
 *   resolved" there means "absent from `card_strategy_tags_cache`", NOT `tags = '[]'` (a card
 *   genuinely resolved to zero tags must not be re-queried forever). Only this query drives
 *   [Result.hasMoreWork].
 * - [CardDao.getScryfallIdsWithUnwrittenStrategyTags] — the repair net for rows the ORACLE-keyed
 *   candidate query can no longer reach (see the per-printing write gap below). Capped, network-free
 *   in practice (its oracle_ids are cached by definition), and deliberately excluded from the retry
 *   chain since it is a stable fixed point rather than a shrinking queue.
 *
 * ## Per-printing write gap (the reason resolution fans out by oracle_id)
 * Tags are an ORACLE-wide property but live in a per-`scryfall_id` column. The candidate query
 * excludes a card as soon as ANY row with its `oracle_id` is cached, so when a user owns two
 * printings of one card, the printing resolved first cached the oracle_id and the second was
 * excluded before it was ever written — permanently, since it could never become a candidate again
 * (8 such rows measured on a real device). The same shape stranded any card whose write did not
 * land before an interrupted run died, because the batch caches every oracle_id up front.
 *
 * The fix is structural: a resolved payload is applied to EVERY cached `cards` row sharing that
 * `oracle_id` ([CardDao.getByOracleIds]), not just the candidate `scryfall_id`. Each row is still
 * resolved through [ResolveCardStrategyTagsUseCase.resolveWithPrefetched] with its OWN `tags`
 * column, so a per-printing user-confirmed tag is unioned in and never clobbered by a sibling's
 * payload — which is exactly why this is not a single `UPDATE ... WHERE oracle_id = ?`.
 *
 * ## Cost
 * One page of candidates, grouped by `oracle_id`, resolved by ONE batched
 * [CardStrategyTagsRepository.getStrategyTagsBatch] call (~13 requests for 1300 cards, per
 * ADR-005), written back in chunked transactions rather than one Room invalidation per row.
 * Cards the batch misses (no pipeline row yet) fall back to the full on-device analyzer path, but
 * only [onDeviceCap] of them per run — that path is CPU-heavy plus a per-card EDHREC check. Their
 * sibling printings are picked up by the repair query on a later run.
 */
class HydrateCollectionStrategyTagsUseCase(
    private val cardDao: CardDao,
    private val cardStrategyTagsRepository: CardStrategyTagsRepository,
    private val resolveCardStrategyTags: ResolveCardStrategyTagsUseCase,
    private val userPreferences: UserPreferencesDataStore,
    private val crashReporter: CrashReporter,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * @param candidateCount ids this run picked up across both candidate queries.
     * @param precomputedCount rows written from the batched precomputed source, siblings included.
     * @param repairedCount subset of [precomputedCount] whose `scryfall_id` was NOT itself a
     *   candidate — i.e. sibling printings and rows stranded by an earlier interrupted run.
     * @param onDeviceCount cards resolved by the on-device fallback (capped, see [onDeviceCap]).
     * @param hasMoreWork true when another run should follow immediately. Deliberately gated on
     *   a precomputed hit against the PRIMARY candidate query, never on "candidates remain": a
     *   precomputed hit always writes a cache row, so it provably shrinks that query's result and
     *   the chain terminates. A page of pure misses (whose device write-back may or may not land),
     *   or the repair query's stable fixed point, would otherwise re-select the same ids forever —
     *   an unbounded worker chain, which is exactly what ADR-005 forbids.
     */
    data class Result(
        val candidateCount: Int,
        val precomputedCount: Int,
        val repairedCount: Int,
        val onDeviceCount: Int,
        val hasMoreWork: Boolean,
    )

    suspend operator fun invoke(
        batchSize: Int = DEFAULT_BATCH_SIZE,
        onDeviceCap: Int = DEFAULT_ON_DEVICE_CAP,
        repairCap: Int = DEFAULT_REPAIR_CAP,
    ): Result = withContext(ioDispatcher) {
        val missingIds = query("candidate_query") { cardDao.getScryfallIdsMissingStrategyTags(batchSize) }
        val unwrittenIds = query("repair_query") { cardDao.getScryfallIdsWithUnwrittenStrategyTags(repairCap) }
        val candidateIds = (missingIds + unwrittenIds).distinct()
        if (candidateIds.isEmpty()) return@withContext EMPTY_RESULT

        val candidates = candidateIds.chunked(ROOM_LOOKUP_CHUNK)
            .flatMap { chunk -> runCatching { cardDao.getByIds(chunk) }.getOrElse { emptyList() } }
            .filter { it.oracleId.isNotBlank() }
        if (candidates.isEmpty()) return@withContext EMPTY_RESULT.copy(candidateCount = candidateIds.size)

        val resolved = runCatching {
            cardStrategyTagsRepository.getStrategyTagsBatch(candidates.mapTo(mutableSetOf()) { it.oracleId })
        }.getOrElse { e ->
            recordFailure("batch_fetch", e)
            emptyMap()
        }

        val hits = resolved.filterValues { it is CardStrategyTagsResult.Found }
        val (precomputedCount, repairedCount) = applyHits(hits, candidateIds.toSet())

        val misses = candidates.filter { it.oracleId !in hits }
        val onDeviceCount = runOnDeviceFallback(misses.take(onDeviceCap))

        crashReporter.log("card_tag_hydration_run")
        crashReporter.setCustomKey("tag_hydration_candidates", candidateIds.size.toString())
        crashReporter.setCustomKey("tag_hydration_precomputed", precomputedCount.toString())
        crashReporter.setCustomKey("tag_hydration_repaired", repairedCount.toString())
        crashReporter.setCustomKey("tag_hydration_on_device", onDeviceCount.toString())

        Result(
            candidateCount = candidateIds.size,
            precomputedCount = precomputedCount,
            repairedCount = repairedCount,
            onDeviceCount = onDeviceCount,
            hasMoreWork = precomputedCount > 0 && missingIds.size >= batchSize,
        )
    }

    /** Fans each resolved oracle_id out to every cached printing of that card, unioning per row.
     *  @return written-row count, and how many of those were not themselves candidates. */
    private suspend fun applyHits(
        hits: Map<String, CardStrategyTagsResult>,
        candidateIds: Set<String>,
    ): Pair<Int, Int> {
        if (hits.isEmpty()) return 0 to 0

        val printings = hits.keys.toList().chunked(ROOM_LOOKUP_CHUNK)
            .flatMap { chunk -> runCatching { cardDao.getByOracleIds(chunk) }.getOrElse { emptyList() } }

        val updates = printings.mapNotNull { entity ->
            val hit = hits[entity.oracleId] ?: return@mapNotNull null
            val result = runCatching {
                resolveCardStrategyTags.resolveWithPrefetched(entity.toDomainCard(), entity.tags, hit)
            }.getOrElse { e ->
                recordFailure("apply_hit", e)
                return@mapNotNull null
            }
            CardTagsUpdate(
                scryfallId = entity.scryfallId,
                tagsJson = result.confirmedTags.toTagsJson(),
                suggestedJson = result.suggestedTags.toSuggestedTagsJson(),
            )
        }

        var written = 0
        updates.chunked(WRITE_CHUNK).forEach { chunk ->
            runCatching { cardDao.updateTagsAndSuggestionsBatch(chunk) }
                .onSuccess { written += chunk.size }
                .onFailure { e -> recordFailure("persist", e) }
        }
        val repaired = updates.count { it.scryfallId !in candidateIds }
        return written to if (written == 0) 0 else repaired
    }

    /** Sequential, never parallel — same rationale as `CardRepositoryImpl.backfillMissingOracleIds`:
     *  every one of these does a network round-trip through an already-globally-rate-limited queue,
     *  so concurrency buys nothing and only starves foreground calls. */
    private suspend fun runOnDeviceFallback(misses: List<CardEntity>): Int {
        if (misses.isEmpty()) return 0
        val auto = runCatching { userPreferences.tagAutoThresholdFlow.first() }.getOrNull()
        val suggest = runCatching { userPreferences.tagSuggestThresholdFlow.first() }.getOrNull()
        var count = 0
        misses.forEach { entity ->
            val result = runCatching {
                if (auto != null && suggest != null) {
                    resolveCardStrategyTags(entity.toDomainCard(), entity.tags, auto, suggest)
                } else {
                    resolveCardStrategyTags(entity.toDomainCard(), entity.tags)
                }
            }.getOrElse { e ->
                recordFailure("on_device_resolve", e)
                null
            } ?: return@forEach
            val persisted = runCatching {
                cardDao.updateTagsAndSuggestions(
                    scryfallId    = entity.scryfallId,
                    tagsJson      = result.confirmedTags.toTagsJson(),
                    suggestedJson = result.suggestedTags.toSuggestedTagsJson(),
                )
            }.onFailure { e -> recordFailure("persist", e) }.isSuccess
            if (persisted) count++
        }
        return count
    }

    private suspend fun query(stage: String, block: suspend () -> List<String>): List<String> =
        runCatching { block() }.getOrElse { e ->
            recordFailure(stage, e)
            emptyList()
        }

    private fun recordFailure(stage: String, e: Throwable) {
        crashReporter.setCustomKey("tag_hydration_stage", stage)
        crashReporter.recordException(RuntimeException("[HydrateCollectionStrategyTags] $stage failed", e))
    }

    companion object {
        /** One batched page. 1000 candidates resolve in ~10 chunked requests, so a 1300-card
         *  backlog drains in two runs. */
        const val DEFAULT_BATCH_SIZE = 1000

        /** Per-run ceiling on the CPU-heavy on-device analyzer path (see the class KDoc). */
        const val DEFAULT_ON_DEVICE_CAP = 60

        /** Per-run ceiling on the network-free repair query (see [CardDao.getScryfallIdsWithUnwrittenStrategyTags]). */
        const val DEFAULT_REPAIR_CAP = 500

        /** SQLite caps host parameters per statement; chunk every `IN (:ids)` read below it. */
        private const val ROOM_LOOKUP_CHUNK = 400

        /** Rows per write transaction — bounded lock hold, one Room invalidation per chunk rather
         *  than per row (`feedback_stats_room_invalidation_oom`). */
        private const val WRITE_CHUNK = 500

        private val EMPTY_RESULT = Result(
            candidateCount = 0,
            precomputedCount = 0,
            repairedCount = 0,
            onDeviceCount = 0,
            hasMoreWork = false,
        )
    }
}
