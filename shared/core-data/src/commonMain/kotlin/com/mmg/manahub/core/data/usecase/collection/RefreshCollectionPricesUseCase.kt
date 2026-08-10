package com.mmg.manahub.core.data.usecase.collection

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.repository.CachePolicy
import com.mmg.manahub.core.domain.repository.CardPriceUpdate
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Refreshes STALE collection card prices by batch-fetching from Scryfall and writing updated
 * prices to the local card repository, in bounded SLICES.
 *
 * Backend & Performance Optimization plan, WS1+WS3 Part B item 7 (2026-07-28) reworked this from a
 * single-pass, whole-collection, in-memory-accumulating refresh into a resumable, stale-only,
 * sliced one:
 * - **Only stale ids** (item 7f): a card's `cached_at` column doubles as its price-freshness
 *   timestamp (`CardDao.updatePrices`/`updatePricesBatch` both write it on every price update), so
 *   [CachePolicy.isFresh] over [CardRepository.getCardsByIds] (Room-only, no network) reproduces the
 *   filter the now-deleted `CardRepositoryImpl.refreshCollectionPrices()` used to apply — a second
 *   run the same day is a near-no-op.
 * - **Sliced writes** (item 7c): at most [SLICE_SIZE] ids (7 chunks of [CHUNK_SIZE]) are
 *   accumulated in memory before ONE [CardRepository.updatePricesBatch] call — bounded memory AND
 *   only a handful of Room invalidations per run, instead of either "N chunks = N writes" (the
 *   invalidation storm behind `feedback_stats_room_invalidation_oom`) or "the whole collection in
 *   one write" (thousands of objects held at once for a large collection).
 * - **Capped per run + naturally resumable, no persisted cursor** (items 7d/7e): [maxSlices] bounds
 *   how much work one [invoke] call does. There is DELIBERATELY no persisted slice-index cursor in
 *   DataStore: since each already-written slice's ids become fresh (their `cached_at` is bumped)
 *   the MOMENT [CardRepository.updatePricesBatch] returns, the next [invoke] call — whether a
 *   worker retry after a process death, or a scheduled follow-up after hitting [maxSlices] —
 *   naturally recomputes a SMALLER stale-id list that already excludes everything the previous call
 *   finished. An index-based cursor would be actively WRONG here: recomputing the stale-id list
 *   fresh each call (required for 7f) shifts every subsequent id's position, so an old numeric
 *   offset would point at the wrong slice. [Result.Capped] tells the caller (a) not to claim the
 *   daily watermark and (b) that a follow-up run is needed; [Result.Success] means every id that was
 *   stale AT THE START of this call is now fresh.
 * - **Cache coherence** (item 7g): after each slice's Room write, the touched ids' in-memory
 *   [com.mmg.manahub.core.data.network.ScryfallCache.cards] entries are invalidated (see
 *   [ScryfallRemoteDataSource.invalidateCachedCards]) so they don't keep serving a pre-refresh price
 *   for the rest of their TTL.
 *
 * @param userCardRepository provides the list of Scryfall IDs in the collection.
 * @param cardRepository     the target for price updates (and the stale-id freshness check).
 * @param scryfallDataSource rate-limited Scryfall API access.
 * @param dispatcherProvider KMP-safe dispatcher abstraction (replaces `Dispatchers.IO`).
 */
@OptIn(ExperimentalTime::class)
class RefreshCollectionPricesUseCase(
    private val userCardRepository: UserCardRepository,
    private val cardRepository: CardRepository,
    private val scryfallDataSource: ScryfallRemoteDataSource,
    private val dispatcherProvider: DispatcherProvider,
) {
    sealed class Result {
        /** A full pass completed: every id that was stale at the start of this call is now fresh. */
        data class Success(
            val updatedCount: Int,
            val notFoundCount: Int,
            val durationMs: Long,
        ) : Result()

        /**
         * [maxSlices] was reached before every stale id was processed this call.
         * [remainingStaleCount] ids are still stale — the caller must NOT claim the daily watermark
         * and should schedule a follow-up run (see the class KDoc's "naturally resumable" note: a
         * follow-up call needs no cursor, it will simply see a smaller stale-id list).
         */
        data class Capped(
            val updatedCount: Int,
            val notFoundCount: Int,
            val remainingStaleCount: Int,
        ) : Result()

        data class Error(val message: String) : Result()
        data class Progress(val current: Int, val total: Int) : Result()
    }

    /**
     * @param maxSlices upper bound on how many [SLICE_SIZE]-sized slices this call processes before
     *   yielding (emitting [Result.Capped] instead of continuing) — a huge collection must not
     *   monopolise the Scryfall budget in one run.
     */
    fun invoke(maxSlices: Int = DEFAULT_MAX_SLICES_PER_RUN): Flow<Result> = flow {
        val startTime = Clock.System.now().toEpochMilliseconds()
        try {
            val allIds = userCardRepository.getScryfallIds().distinct()
            if (allIds.isEmpty()) {
                emit(Result.Success(0, 0, 0))
                return@flow
            }

            // Only refresh what is stale (item 7f) — see class KDoc.
            val cachedMap = cardRepository.getCardsByIds(allIds).associateBy { it.scryfallId }
            val staleIds = allIds.filter { id ->
                cachedMap[id]?.let { !CachePolicy.isFresh(it.cachedAt) } ?: true
            }
            if (staleIds.isEmpty()) {
                emit(Result.Success(0, 0, 0))
                return@flow
            }

            val allSlices = staleIds.chunked(SLICE_SIZE)
            val slicesThisRun = allSlices.take(maxSlices)
            val totalChunks = staleIds.chunked(CHUNK_SIZE).size
            var chunksDone = 0
            var updatedTotal = 0
            var notFoundTotal = 0

            slicesThisRun.forEach { slice ->
                val sliceUpdates = mutableListOf<CardPriceUpdate>()
                var sliceNotFound = 0

                slice.chunked(CHUNK_SIZE).forEach { chunk ->
                    chunksDone++
                    emit(Result.Progress(current = chunksDone, total = totalChunks))

                    val response = scryfallDataSource.getCardCollection(chunk)
                    val now = Clock.System.now().toEpochMilliseconds()

                    response.data.forEach { cardDto ->
                        val prices = cardDto.prices
                        sliceUpdates.add(
                            CardPriceUpdate(
                                scryfallId = cardDto.id,
                                priceUsd = prices.usd?.toDoubleOrNull(),
                                priceUsdFoil = prices.usdFoil?.toDoubleOrNull(),
                                priceEur = prices.eur?.toDoubleOrNull(),
                                priceEurFoil = prices.eurFoil?.toDoubleOrNull(),
                                updatedAt = now,
                            )
                        )
                    }
                    sliceNotFound += response.notFound.size
                }

                // ONE Room write per slice (item 7c) — never per 75-card chunk, never the whole run.
                cardRepository.updatePricesBatch(sliceUpdates)
                // Cache coherence (item 7g) — see class KDoc.
                scryfallDataSource.invalidateCachedCards(slice)

                updatedTotal += sliceUpdates.size
                notFoundTotal += sliceNotFound
            }

            val remaining = staleIds.size - slicesThisRun.sumOf { it.size }
            if (remaining > 0) {
                emit(Result.Capped(updatedTotal, notFoundTotal, remaining))
            } else {
                emit(
                    Result.Success(
                        updatedCount = updatedTotal,
                        notFoundCount = notFoundTotal,
                        durationMs = Clock.System.now().toEpochMilliseconds() - startTime,
                    )
                )
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(dispatcherProvider.io)

    companion object {
        private const val CHUNK_SIZE = 75

        /** ~525 ids per slice (7 chunks of [CHUNK_SIZE]) — one bounded Room write per slice. */
        const val SLICE_SIZE = CHUNK_SIZE * 7

        /**
         * Default cap: 6 slices (~3150 ids) per [invoke] call before yielding [Result.Capped]. A
         * collection larger than this needs more than one worker run (each capped run's already-
         * fresh ids are naturally excluded from the next run's recomputed stale-id list).
         */
        const val DEFAULT_MAX_SLICES_PER_RUN = 6
    }
}
