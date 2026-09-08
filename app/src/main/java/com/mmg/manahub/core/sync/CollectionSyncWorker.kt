package com.mmg.manahub.core.sync
// COMMENTS_REVIEWED: 2026-09-06

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
 * Retry policy: exponential backoff starting at 15 minutes, up to 3 attempts.
 */
class CollectionSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val syncManager: SyncManager,
    private val authRepository: AuthRepository,
) : CoroutineWorker(appContext, workerParams) {

    companion object {

        const val WORK_NAME_PERIODIC = "collection_sync_periodic"

        /** Unique name for on-demand (one-time) incremental sync tasks. */
        const val WORK_NAME_ONE_TIME = "collection_sync_one_time"

        // Write-path hardening audit (2026-09-06): split from WORK_NAME_ONE_TIME -- both used to
        // share that name, so ExistingWorkPolicy.KEEP could drop a first-login enqueue when a
        // plain sync was already pending, silently skipping the guest-row migration.
        const val WORK_NAME_FIRST_LOGIN = "collection_sync_first_login"

        const val INPUT_KEY_IS_FIRST_LOGIN = "is_first_login"

        fun periodicWorkRequest() =
            PeriodicWorkRequestBuilder<CollectionSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

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
         * Offline-to-online first-login full pull ([SyncManager.assignUserIdAndSync]). Runs as
         * durable WorkManager unique work (not `viewModelScope`) so it survives navigation and
         * process death instead of leaving a first-login account partially migrated.
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

        fun schedulePeriodicSync(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest(),
            )
        }

        /** Enqueues an immediate incremental sync (e.g. a manual "Sync now" tap). */
        fun enqueueOneTimeSync(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.KEEP,
                oneTimeWorkRequest(),
            )
        }

        /**
         * Enqueues the first-login migration under its own unique name (never
         * [WORK_NAME_ONE_TIME] -- see that constant's KDoc) so it can never be dropped by a
         * plain sync's KEEP policy. A plain sync already queued is cancelled first: first-login
         * is a strict push+pull superset, and racing it risks pushing not-yet-migrated guest
         * rows (still `user_id IS NULL`) before [SyncManager.assignUserId] runs.
         */
        fun enqueueFirstLoginSync(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME_ONE_TIME)
            workManager.enqueueUniqueWork(
                WORK_NAME_FIRST_LOGIN,
                ExistingWorkPolicy.KEEP,
                oneTimeWorkRequestForFirstLogin(),
            )
        }
    }

    override suspend fun doWork(): Result {
        val userId = authRepository.getCurrentUser()?.id ?: return Result.success()

        return try {
            val isFirstLogin = inputData.getBoolean(INPUT_KEY_IS_FIRST_LOGIN, false)
            val result = if (isFirstLogin) {
                syncManager.assignUserIdAndSync(userId)
            } else {
                syncManager.sync(userId)
            }
            if (result.state == SyncState.ERROR) {
                Result.retry()
            } else {
                // A successful cycle may have just written pending-hydration placeholders
                // (SyncManager.ensureCardsExist) -- kick hydration off now instead of waiting for
                // CardHydrationWorker's hourly tick.
                val workManager = WorkManager.getInstance(applicationContext)
                CardHydrationWorker.enqueueImmediate(workManager)
                // A pull is the only event that introduces cards whose strategy tags this device
                // has never resolved -- hydrate them in batched passes off the sync's critical path.
                CardTagHydrationWorker.enqueueImmediate(workManager)
                Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
