package com.mmg.manahub.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic background worker that runs the full LWW delta-sync cycle via [SyncManager].
 *
 * Scheduled every hour when the device has network connectivity. If the sync fails
 * it retries with exponential backoff (up to 3 attempts) before being marked as
 * failure. Guest sessions (no authenticated user) are silently skipped.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager,
    private val authRepository: AuthRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val userId = authRepository.getCurrentUser()?.id
            ?: return Result.success() // guest — nothing to sync

        return try {
            val result = syncManager.sync(userId)
            if (result.state == SyncState.ERROR && runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "manahub_periodic_sync"
        private const val MAX_RETRIES = 3

        fun buildRequest() = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
    }
}
