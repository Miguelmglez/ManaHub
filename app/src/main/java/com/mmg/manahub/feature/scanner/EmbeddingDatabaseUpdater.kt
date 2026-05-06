package com.mmg.manahub.feature.scanner

// COMMENTED OUT — replaced by ML Kit OCR. See CardRecognizer for the new pipeline.
/*
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules a one-time [EmbeddingDatabaseUpdateWorker] via WorkManager.
 *
 * Uses [ExistingWorkPolicy.REPLACE] so that any pending backoff from a previous
 * failed attempt is cancelled and the check runs immediately on the next app launch.
 * A RUNNING job is replaced only if constraints allow; in practice the worker
 * returns SUCCESS on the first successful download, so subsequent launches are
 * cheap no-ops (version check only).
 *
 * Injected into [com.mmg.manahub.app.ManaHubApp] and called once per app launch.
 */
@Singleton
class EmbeddingDatabaseUpdater @Inject constructor(
    private val workManager: WorkManager,
) {

    /**
     * Enqueues a unique one-time work request to check for and download a newer
     * version of `card_embeddings.bin` from Cloudflare R2.
     *
     * Any previously pending/backoff-delayed work is replaced so the check
     * starts immediately (subject to network constraint).
     */
    fun scheduleUpdateCheck() {
        workManager.enqueueUniqueWork(
            EmbeddingDatabaseUpdateWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            EmbeddingDatabaseUpdateWorker.buildRequest(),
        )
    }
}
*/
