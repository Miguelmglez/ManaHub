package com.mmg.manahub.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that delegates to [SyncManager] for bidirectional sync.
 *
 * Annotated with [HiltWorker] so Hilt can inject dependencies via [AssistedInject].
 * Only runs when [NetworkType.CONNECTED] is satisfied.
 *
 * Retry policy: exponential backoff starting at 15 minutes, up to 3 attempts
 * before the worker is marked as failed.
 */
@HiltWorker
class CollectionSyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncManager: SyncManager,
    private val authRepository: AuthRepository,
) : CoroutineWorker(appContext, workerParams) {

    companion object {

        /** Unique name for the periodic background sync task. */
        const val WORK_NAME_PERIODIC = "collection_sync_periodic"

        /** Unique name for on-demand (one-time) sync tasks. */
        const val WORK_NAME_ONE_TIME = "collection_sync_one_time"

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
         * Builds a [OneTimeWorkRequest] for an immediate sync triggered by the user
         * or by the offline-to-online transition.
         */
        fun oneTimeWorkRequest() =
            OneTimeWorkRequestBuilder<CollectionSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
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
    }

    override suspend fun doWork(): Result {
        // Guest users have no Supabase account — skip sync entirely.
        val userId = authRepository.getCurrentUser()?.id ?: return Result.success()

        return try {
            val result = syncManager.sync(userId)
            if (result.state == SyncState.ERROR) {
                // Retry on transient errors (network blip, Supabase timeout, etc.).
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            // Give up after 3 attempts to avoid draining the battery on a persistent failure.
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
