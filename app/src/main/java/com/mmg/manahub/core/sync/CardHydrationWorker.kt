package com.mmg.manahub.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.mapper.toEntityCard
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import java.util.concurrent.TimeUnit

/**
 * Collection sync data-loss fix (`linear-moseying-yeti` plan), Phase 4 — hydration worker.
 *
 * `SyncManager.ensureCardsExist` now writes a `stale_reason = "pending_hydration"` placeholder
 * [com.mmg.manahub.core.data.local.entity.CardEntity] for any `scryfall_id` Scryfall did not
 * return during a collection/deck pull, so the owning `user_card_collection`/`deck_cards` row can
 * ALWAYS insert instead of being silently skipped (the root cause of the permanent data-loss this
 * plan fixes). This worker is the OTHER half of that design: it retries those placeholders against
 * Scryfall until real metadata resolves, chunked 75-at-a-time through
 * [ScryfallRemoteDataSource] (itself routed through the shared `ScryfallRequestQueue`).
 *
 * ## Why a dedicated worker rather than extending [CardBackfillWorker]
 * [CardBackfillWorker] runs once a DAILY, deliberately decoupled from the login-window Scryfall
 * burst (see its own KDoc). A newly-created placeholder is exactly the shape of data that skews
 * Stats/Deck-Analysis aggregates (both now filter `stale_reason == "pending_hydration"` rows out
 * rather than average in `cmc = 0`/empty colors — see this plan's Section C guard audit) and, more
 * importantly, is invisible-looking to the user ("Unresolved card (...)" placeholder name/art)
 * until it resolves. Piggy-backing on a once-daily cadence would leave that exposure window open
 * for up to 24h after a large recovery pull (e.g. the 1377-row incident this plan recovers). A
 * SEPARATE hourly periodic worker, PLUS an immediate one-time follow-up enqueued right after a
 * sync that actually created placeholders ([CollectionSyncWorker] calls [enqueueImmediate]), keeps
 * that window small without competing with [CardBackfillWorker]'s own daily budget.
 *
 * Serializes with an in-progress collection sync the same way [CardBackfillWorker] does (defers
 * via [androidx.work.ListenableWorker.Result.retry] while [SyncManager.syncState] is
 * [SyncState.SYNCING]) so it never competes with the sync's own Scryfall calls for rate-limit
 * budget.
 */
class CardHydrationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val cardDao: CardDao,
    private val scryfallRemote: ScryfallRemoteDataSource,
    private val syncManager: SyncManager,
    private val crashReporter: CrashReporter,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (syncManager.syncState.value == SyncState.SYNCING) return Result.retry()

        val pendingIds = cardDao.getPendingHydrationIds(MAX_IDS_PER_RUN)
        if (pendingIds.isEmpty()) return Result.success()

        var resolvedCount = 0
        pendingIds.chunked(SCRYFALL_CHUNK_SIZE).forEach { chunk ->
            scryfallRemote.getCardsBatch(chunk)
                .onSuccess { cards ->
                    if (cards.isNotEmpty()) {
                        // Safe INSERT-OR-IGNORE + @Update transaction (never REPLACE) -- overwrites
                        // the placeholder row in place, so it can never CASCADE-strip the
                        // user_card_collection/deck_cards rows referencing it (CardDao class KDoc).
                        runCatching { cardDao.upsertAll(cards.map { it.toEntityCard() }) }
                            .onSuccess { resolvedCount += cards.size }
                            .onFailure { e ->
                                crashReporter.apply {
                                    setCustomKey("sync_error_type", e::class.simpleName ?: "Unknown")
                                    recordException(RuntimeException("[CardHydrationWorker] upsertAll failed", e))
                                }
                            }
                    }
                }
                .onFailure { e ->
                    // Non-fatal: this chunk's ids stay pending-hydration and are retried on the
                    // next run (periodic or the immediate follow-up below).
                    crashReporter.apply {
                        setCustomKey("sync_error_type", e::class.simpleName ?: "Unknown")
                        recordException(RuntimeException("[CardHydrationWorker] getCardsBatch failed", e))
                    }
                }
        }

        crashReporter.apply {
            log("card_hydration_worker_completed")
            setCustomKey("hydration_resolved_count", resolvedCount.toString())
            setCustomKey("hydration_candidate_count", pendingIds.size.toString())
        }

        // Hit the per-run cap -- there may be more candidates. Schedule an immediate follow-up so
        // a large backlog (e.g. right after a big recovery re-pull) finishes soon instead of
        // waiting for the next periodic tick.
        if (pendingIds.size >= MAX_IDS_PER_RUN) {
            enqueueImmediate(WorkManager.getInstance(applicationContext))
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME_PERIODIC = "card_hydration_periodic"
        const val WORK_NAME_ONE_TIME = "card_hydration_one_time"

        private const val MAX_IDS_PER_RUN = 300
        private const val SCRYFALL_CHUNK_SIZE = 75

        fun periodicWorkRequest() =
            PeriodicWorkRequestBuilder<CardHydrationWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                .build()

        fun oneTimeWorkRequest() =
            OneTimeWorkRequestBuilder<CardHydrationWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                .build()

        fun schedulePeriodic(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest(),
            )
        }

        /**
         * Enqueues an immediate one-time hydration pass. [CollectionSyncWorker] calls this right
         * after a sync completes so a freshly-created placeholder resolves within the hour cadence
         * rather than waiting for the next periodic tick — see the class KDoc for why this
         * matters. [ExistingWorkPolicy.KEEP] means a rapid succession of syncs (or this worker's
         * own capped-run follow-up) never piles up duplicate runs.
         */
        fun enqueueImmediate(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.KEEP,
                oneTimeWorkRequest(),
            )
        }
    }
}
