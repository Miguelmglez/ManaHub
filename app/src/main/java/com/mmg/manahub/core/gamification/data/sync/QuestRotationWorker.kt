package com.mmg.manahub.core.gamification.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mmg.manahub.core.gamification.engine.QuestReconciler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that rolls quests over once a day (ADR-002, Phase 2).
 *
 * Delegates entirely to [QuestReconciler] (idempotent), so the app-start reconcile and this worker can
 * both run without conflict. No network constraint — quests are 100% local (ADR-002 §11). Mirrors
 * [com.mmg.manahub.core.sync.CollectionSyncWorker]'s `@HiltWorker` + `@AssistedInject` shape; the
 * [androidx.hilt.work.HiltWorkerFactory] wiring that already powers `CollectionSyncWorker` covers this
 * worker too.
 */
@HiltWorker
class QuestRotationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val questReconciler: QuestReconciler,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result =
        runCatching { questReconciler.reconcile() }.fold(
            onSuccess = { Result.success() },
            // Transient failure (DB locked, etc.) — retry up to 3 attempts, then give up so we don't
            // spin forever on a persistent error.
            onFailure = { if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure() },
        )

    companion object {

        /** Unique name for the daily quest-rotation work. */
        const val WORK_NAME = "quest_rotation"

        private const val MAX_ATTEMPTS = 3

        /**
         * Schedules the daily rotation with an initial delay until the next local midnight (so the first
         * run lines up with the day boundary), then every 24h. [ExistingPeriodicWorkPolicy.KEEP] makes
         * repeated calls (every app start) no-ops once scheduled. No network constraint.
         *
         * [clock] / [zoneId] default to the system clock/zone for the production call site; tests can
         * pin them to assert the computed initial delay.
         */
        fun scheduleDaily(
            workManager: WorkManager,
            clock: Clock = Clock.systemDefaultZone(),
            zoneId: ZoneId = ZoneId.systemDefault(),
        ) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<QuestRotationWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(initialDelayToNextMidnightMillis(clock, zoneId), TimeUnit.MILLISECONDS)
                    .build(),
            )
        }

        /** Milliseconds from "now" (per [clock]) until the next local midnight in [zoneId]. */
        internal fun initialDelayToNextMidnightMillis(clock: Clock, zoneId: ZoneId): Long {
            val now = clock.instant().atZone(zoneId)
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zoneId)
            return (nextMidnight.toInstant().toEpochMilli() - now.toInstant().toEpochMilli())
                .coerceAtLeast(0L)
        }
    }
}
