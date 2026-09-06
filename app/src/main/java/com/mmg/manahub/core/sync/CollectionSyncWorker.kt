package com.mmg.manahub.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mmg.manahub.core.domain.auth.AuthRepository
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that delegates to [SyncManager] for bidirectional sync.
 *
 * Only runs when [NetworkType.CONNECTED] is satisfied.
 *
 * Retry policy: exponential backoff starting at 15 minutes, up to 3 attempts
 * before the worker is marked as failed.
 *
 * KMP migration — Hilt→Koin cutover batch 6: converted from `@HiltWorker`/`@AssistedInject` to a plain
 * [CoroutineWorker] resolved by Koin's `worker { }` DSL, registered in `feature.collection.di.collectionKoinModule`
 * (co-located with the [SyncManager] bridge single it needs — [SyncManager] itself KEEPS its Hilt
 * `@Inject constructor` because `ManaHubApp` (`@AndroidEntryPoint`) still Hilt-injects it as a bridge
 * field into `collectionKoinModule(syncManager = ...)`; [authRepository] is a native Koin single in
 * `coreBridgeKoinModule`).
 */
class CollectionSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val syncManager: SyncManager,
    private val authRepository: AuthRepository,
) : CoroutineWorker(appContext, workerParams) {

    companion object {

        /** Unique name for the periodic background sync task. */
        const val WORK_NAME_PERIODIC = "collection_sync_periodic"

        /** Unique name for on-demand (one-time) sync tasks. */
        const val WORK_NAME_ONE_TIME = "collection_sync_one_time"

        /**
         * Input-data key (collection sync data-loss fix, Phase 5). When `true`, [doWork]
         * dispatches [SyncManager.assignUserIdAndSync] instead of [SyncManager.sync] — the
         * offline-to-online first-login full pull, which must survive the triggering screen being
         * navigated away from or the process dying (see [oneTimeWorkRequestForFirstLogin]'s KDoc).
         */
        const val INPUT_KEY_IS_FIRST_LOGIN = "is_first_login"

        /**
         * Builds a [PeriodicWorkRequest] that runs every hour with exponential
         * backoff on failure, requiring a network connection.
         */
        fun periodicWorkRequest() =
            PeriodicWorkRequestBuilder<CollectionSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

        /**
         * Builds a [OneTimeWorkRequest] for an immediate incremental sync triggered by the user
         * (e.g. a manual "Sync now" tap). Runs [SyncManager.sync].
         */
        fun oneTimeWorkRequest() =
            OneTimeWorkRequestBuilder<CollectionSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

        /**
         * Builds a [OneTimeWorkRequest] for the offline-to-online first-login full pull
         * ([SyncManager.assignUserIdAndSync]).
         *
         * Collection sync data-loss fix, Phase 5: this used to run inline on `viewModelScope`
         * (`CollectionViewModel.observeSessionChanges`), so navigating away from the screen that
         * triggered login cancelled the first full pull mid-flight — a first-login collection
         * could be left partially migrated with no automatic retry. WorkManager unique work
         * survives navigation and process death; [ExistingWorkPolicy.KEEP] on
         * [WORK_NAME_ONE_TIME] means a rapid double-trigger (e.g. a second `Authenticated` emit
         * from profile enrichment) never queues a duplicate run.
         */
        fun oneTimeWorkRequestForFirstLogin() =
            OneTimeWorkRequestBuilder<CollectionSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .setInputData(Data.Builder().putBoolean(INPUT_KEY_IS_FIRST_LOGIN, true).build())
                .build()

        /**
         * Convenience helper to enqueue the periodic sync from a ViewModel or Application.
         * Uses [ExistingPeriodicWorkPolicy.KEEP] so repeated calls are no-ops if the
         * worker is already scheduled.
         */
        fun schedulePeriodicSync(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest(),
            )
        }

        /**
         * Enqueues an immediate one-time incremental sync (e.g. a manual "Sync now" action).
         * [ExistingWorkPolicy.KEEP] avoids piling up duplicate runs if tapped repeatedly.
         */
        fun enqueueOneTimeSync(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.KEEP,
                oneTimeWorkRequest(),
            )
        }

        /**
         * Enqueues the offline-to-online first-login full pull as durable unique work — see
         * [oneTimeWorkRequestForFirstLogin]'s KDoc for why this must not run on a scope tied to a
         * screen's lifecycle.
         */
        fun enqueueFirstLoginSync(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.KEEP,
                oneTimeWorkRequestForFirstLogin(),
            )
        }
    }

    override suspend fun doWork(): Result {
        // Guest users have no Supabase account — skip sync entirely.
        val userId = authRepository.getCurrentUser()?.id ?: return Result.success()

        return try {
            val isFirstLogin = inputData.getBoolean(INPUT_KEY_IS_FIRST_LOGIN, false)
            val result = if (isFirstLogin) {
                syncManager.assignUserIdAndSync(userId)
            } else {
                syncManager.sync(userId)
            }
            if (result.state == SyncState.ERROR) {
                // Retry on transient errors (network blip, Supabase timeout, etc.).
                Result.retry()
            } else {
                // Collection sync data-loss fix, Phase 4: a successful cycle may have just
                // written pending-hydration placeholders (SyncManager.ensureCardsExist). Kick off
                // an immediate hydration pass rather than waiting for CardHydrationWorker's hourly
                // tick — see that worker's KDoc for why the exposure window matters.
                CardHydrationWorker.enqueueImmediate(WorkManager.getInstance(applicationContext))
                Result.success()
            }
        } catch (e: CancellationException) {
            // Let WorkManager's own cooperative cancellation propagate (e.g. constraints no
            // longer met, or the work was explicitly cancelled) instead of misreporting it as a
            // retryable/failed run.
            throw e
        } catch (e: Exception) {
            // Give up after 3 attempts to avoid draining the battery on a persistent failure.
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
