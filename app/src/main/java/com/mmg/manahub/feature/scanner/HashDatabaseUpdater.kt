package com.mmg.manahub.feature.scanner

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the [HashDatabaseUpdateWorker] as a unique one-time job.
 *
 * Using [ExistingWorkPolicy.KEEP] means repeated calls (e.g. on every app launch)
 * are no-ops if the worker is already enqueued or running.
 */
@Singleton
class HashDatabaseUpdater @Inject constructor(
    private val workManager: WorkManager,
) {
    /** Enqueues the hash-DB update check if it is not already queued or running. */
    fun scheduleUpdateCheck() {
        workManager.enqueueUniqueWork(
            HashDatabaseUpdateWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            HashDatabaseUpdateWorker.buildRequest(),
        )
    }
}
