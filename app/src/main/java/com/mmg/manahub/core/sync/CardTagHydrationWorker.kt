package com.mmg.manahub.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.usecase.card.HydrateCollectionStrategyTagsUseCase
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Bulk strategy-tag hydration (2026-09-07). Drains
 * [HydrateCollectionStrategyTagsUseCase]'s candidate backlog in batched passes so a freshly-synced
 * collection is fully tagged within minutes instead of the ~33 days the old 40-cards-a-day
 * [CardBackfillWorker] drip needed.
 *
 * Enqueued one-time (never periodic) from exactly two places, so it only ever runs when there is
 * plausibly work — ADR-005's "runs only when there is work, never per-screen, never on scroll":
 * - [CollectionSyncWorker], right after a successful sync cycle (a pull is the one event that
 *   introduces cards the device has never resolved tags for);
 * - [CardBackfillWorker]'s daily tick, as the long-tail catch-up — that worker no longer runs the
 *   per-card `backfillMissingStrategyTags` drip itself, so the two never double-work the same
 *   candidates.
 *
 * ## Why [Result.retry] rather than self-enqueue for the follow-up run
 * A large backlog needs more than one pass. [CardHydrationWorker] chains passes by re-enqueueing
 * its own unique name, which [ExistingWorkPolicy.KEEP] can drop while that very work is still
 * RUNNING. Returning [Result.retry] instead lets WorkManager schedule the next pass with no
 * unique-name race at all, and the short LINEAR backoff below keeps the chain quick.
 * [HydrateCollectionStrategyTagsUseCase.Result.hasMoreWork] is only ever true after a pass that
 * provably shrank the candidate list, so the chain always terminates (see its KDoc).
 *
 * Defers via [Result.retry] while [SyncManager.syncState] is [SyncState.SYNCING], the same guard
 * [CardBackfillWorker]/[CardHydrationWorker] use, so hydration never competes with the sync's own
 * Supabase budget.
 */
class CardTagHydrationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val hydrateCollectionStrategyTags: HydrateCollectionStrategyTagsUseCase,
    private val syncManager: SyncManager,
    private val crashReporter: CrashReporter,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (syncManager.syncState.value == SyncState.SYNCING) return Result.retry()

        return try {
            val result = hydrateCollectionStrategyTags()
            if (result.hasMoreWork) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            crashReporter.recordException(RuntimeException("[CardTagHydrationWorker] run failed", e))
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        /** Distinct from [CardHydrationWorker.WORK_NAME_ONE_TIME] on purpose — two semantically
         *  different jobs sharing one unique name under KEEP silently drop each other's enqueues. */
        const val WORK_NAME_ONE_TIME = "card_tag_hydration_one_time"

        private const val MAX_ATTEMPTS = 3

        fun oneTimeWorkRequest() =
            OneTimeWorkRequestBuilder<CardTagHydrationWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                // 30s (not the 15min other workers use): a retry here is usually "there is more of
                // the backlog to drain", which should finish in the same minute, not the same hour.
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()

        /**
         * Enqueues an immediate hydration pass. [ExistingWorkPolicy.KEEP] means a burst of syncs
         * (or a sync landing on top of the daily backfill tick) never piles up duplicate passes —
         * the pending run will pick up whatever candidates exist when it starts anyway.
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
