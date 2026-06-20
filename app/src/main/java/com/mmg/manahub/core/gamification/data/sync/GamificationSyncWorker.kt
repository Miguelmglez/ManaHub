package com.mmg.manahub.core.gamification.data.sync

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
import com.mmg.manahub.core.domain.auth.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that delegates to [GamificationSyncManager] for bidirectional gamification sync
 * (ADR-002 §11, Phase 4).
 *
 * Mirrors [com.mmg.manahub.core.sync.CollectionSyncWorker] exactly: `@HiltWorker` + `@AssistedInject`,
 * a periodic (1h) and a one-time builder, [NetworkType.CONNECTED], exponential backoff, and the
 * `getCurrentUser()?.id ?: Result.success()` guest guard. Anonymous guests have a real Supabase id
 * (like collection sync), so this runs for them too.
 *
 * This is SEPARATE from the sibling [QuestRotationWorker] — quests stay unsynced (ADR-002 §11).
 */
@HiltWorker
class GamificationSyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val gamificationSyncManager: GamificationSyncManager,
    private val authRepository: AuthRepository,
) : CoroutineWorker(appContext, workerParams) {

    companion object {

        /** Unique name for the periodic background gamification sync task. */
        const val WORK_NAME_PERIODIC = "gamification_sync_periodic"

        /** Unique name for on-demand (one-time) gamification sync tasks. */
        const val WORK_NAME_ONE_TIME = "gamification_sync_one_time"

        private const val MAX_ATTEMPTS = 3

        /**
         * Builds a [androidx.work.PeriodicWorkRequest] that runs every hour with exponential backoff,
         * requiring a network connection.
         */
        fun periodicWorkRequest() =
            PeriodicWorkRequestBuilder<GamificationSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

        /**
         * Builds a [androidx.work.OneTimeWorkRequest] for an immediate gamification sync (e.g. the
         * sign-in reconcile entry point).
         */
        fun oneTimeWorkRequest() =
            OneTimeWorkRequestBuilder<GamificationSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

        /**
         * Enqueues the periodic gamification sync. [ExistingPeriodicWorkPolicy.KEEP] makes repeated
         * calls (every app start / every auth change) no-ops once scheduled.
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
        // Guest users without any Supabase account (no current user) — skip entirely. Anonymous guests
        // DO have an id, so they sync (local progress is preserved/merged into the anon account).
        val userId = authRepository.getCurrentUser()?.id ?: return Result.success()

        return try {
            gamificationSyncManager.sync(userId).fold(
                onSuccess = { Result.success() },
                // Transient failure (network blip, Supabase timeout) — retry; watermark was not advanced.
                onFailure = { Result.retry() },
            )
        } catch (e: Exception) {
            // Give up after 3 attempts to avoid draining the battery on a persistent failure.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }
}
