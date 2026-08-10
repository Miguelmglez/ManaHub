package com.mmg.manahub.core.gamification.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.util.recordNonFatal
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that delegates to [GamificationSyncManager] for bidirectional gamification sync
 * (ADR-002 §11, Phase 4).
 *
 * Mirrors [com.mmg.manahub.core.sync.CollectionSyncWorker]'s shape: a periodic (1h) and a one-time
 * builder, [NetworkType.CONNECTED], exponential backoff, and the `getCurrentUser()?.id ?:
 * Result.success()` guest guard. Anonymous guests have a real Supabase id (like collection sync), so
 * this runs for them too.
 *
 * KMP migration — Hilt→Koin cutover batch 6: converted from `@HiltWorker`/`@AssistedInject` to a plain
 * [CoroutineWorker] resolved by Koin's `worker { }` DSL, registered in `gamificationEngineKoinModule`
 * ([gamificationSyncManager] and [authRepository] are already native Koin singles there /
 * `coreBridgeKoinModule`). See `core.di.SyncModule.provideWorkManager` for the resulting
 * [androidx.work.DelegatingWorkerFactory] wiring shared with the excluded scanner's Hilt worker.
 *
 * This is SEPARATE from the sibling [QuestRotationWorker] — quests stay unsynced (ADR-002 §11).
 */
class GamificationSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val gamificationSyncManager: GamificationSyncManager,
    private val authRepository: AuthRepository,
    private val userPreferencesDataStore: UserPreferencesDataStore,
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
        // Defense in depth (WS1+WS3 Part A item 3, backend-performance-optimization-plan.md §1):
        // ManaHubApp cancels this worker's unique work reactively when the gamification master
        // flag is off, but an ALREADY-enqueued periodic request (scheduled before the flag was
        // last flipped off, e.g. from a previous install) can still fire before that cancel lands.
        // Re-check the flag here so such a stray run reaches Supabase zero times.
        if (!userPreferencesDataStore.gamificationEnabledFlow.first()) {
            // WS7 telemetry (2026-07-29, ADR-005 Decision 1): this is the direct empirical proof
            // point that the reactive cancel in ManaHubApp actually holds in the field — should fire
            // near-never. Frequent firing means the cancel isn't landing before an already-enqueued
            // periodic run fires (see this method's own class KDoc / defense-in-depth comment above).
            recordNonFatal("gamification_sync_worker_self_aborted_flag_off")
            return Result.success()
        }

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
