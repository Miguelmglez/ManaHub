package com.mmg.manahub.core.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mmg.manahub.core.data.remote.push.PushTokenRemoteDataSource

/**
 * Background retry for a failed token de-registration. Calls [PushTokenRemoteDataSource.delete]
 * and retries (up to 3 attempts) while a network connection is available.
 *
 * KMP migration — Hilt→Koin cutover batch 6: converted from `@HiltWorker`/`@AssistedInject` to a plain
 * [CoroutineWorker] resolved by Koin's `worker { }` DSL, registered in `core.push.di.pushKoinModule`
 * ([dataSource] is forward-bridged there from the same Hilt-built singleton
 * [PushTokenRepositoryImpl][com.mmg.manahub.core.data.repository.PushTokenRepositoryImpl] still uses).
 */
class UnregisterPushTokenWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val dataSource: PushTokenRemoteDataSource,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val token = inputData.getString(KEY_TOKEN) ?: return Result.failure()
        return try {
            dataSource.delete(token)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "push-unregister"
        private const val KEY_TOKEN = "token"

        fun enqueue(workManager: WorkManager, token: String) {
            val request = OneTimeWorkRequestBuilder<UnregisterPushTokenWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(Data.Builder().putString(KEY_TOKEN, token).build())
                .build()
            // REPLACE (not KEEP): if the user re-authenticates while offline and the previous
            // unregister worker is still queued, REPLACE cancels that stale request so it does not
            // silently delete the freshly-registered token after connectivity resumes.
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
