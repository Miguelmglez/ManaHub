package com.mmg.manahub.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mmg.manahub.core.domain.repository.CardRepository
import java.util.concurrent.TimeUnit

/**
 * Backend & Performance Optimization plan, WS1+WS3 Part B item 8 (2026-07-28, §0 F3): moves the
 * oracle-id + strategy-tags opportunistic backfills OFF the cold-start path.
 *
 * Both `CardRepository.backfillMissingOracleIds`/`backfillMissingStrategyTags` used to run
 * unconditionally inside `ManaHubApp.onCreate`'s `appScope` on EVERY app launch — competing with
 * the login-window Scryfall/Supabase burst (collection sync's `ensureCardsExist`, deck sync, Home
 * spotlight, etc.) for the same rate-limit budget. Both are small batches (20/40 candidates),
 * best-effort, failure-silent, and self-terminating (a resolved card drops out of future candidate
 * lists — see each method's own KDoc), so nothing about them needs to run at cold start
 * specifically; a daily background pass is just as effective and never competes with the burst.
 *
 * `NetworkType.CONNECTED` + `setRequiresBatteryNotLow` mirror the "non-critical background
 * maintenance" shape already used by [CollectionStatsSyncWorker]/[PriceRefreshWorker] in this
 * package, plus the extra battery constraint since this work is lower priority still (a catch-up
 * pass over cached data, not user-facing collection/price data).
 *
 * **Ordering invariant preserved from the removed cold-start call**: `backfillMissingOracleIds`
 * MUST run before `backfillMissingStrategyTags` — a blank `oracle_id` card can never have a
 * precomputed `card_strategy_tags` row, so the oracle-id backfill is a prerequisite for the
 * strategy-tags backfill to find real candidates.
 *
 * **Serializes with an in-progress collection sync** (WS1+WS3 item 9): if [SyncManager.syncState]
 * is [SyncState.SYNCING] when this fires, it defers via [androidx.work.ListenableWorker.Result.retry]
 * rather than competing for the same Scryfall/Supabase budget — WorkManager's own backoff handles
 * the re-attempt timing. In practice this is a defensive guard, not the primary fix: moving this
 * work off `ManaHubApp.onCreate` already decouples it from the login-window burst structurally
 * (a daily periodic worker essentially never coincides with cold start).
 */
class CardBackfillWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val cardRepository: CardRepository,
    private val syncManager: SyncManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (syncManager.syncState.value == SyncState.SYNCING) return Result.retry()

        // Both calls are already internally best-effort/failure-silent (see their own KDoc) — the
        // runCatching here is just an extra safety net so a hypothetical uncaught exception from
        // either never fails the whole worker run.
        runCatching { cardRepository.backfillMissingOracleIds(20) }
        // Must run AFTER the oracle-id backfill above — see class KDoc's ordering invariant.
        runCatching { cardRepository.backfillMissingStrategyTags(40) }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "card_backfill_daily"

        fun periodicWorkRequest() =
            PeriodicWorkRequestBuilder<CardBackfillWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                .build()

        fun scheduleDaily(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest(),
            )
        }
    }
}
