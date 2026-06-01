package com.mmg.manahub.core.push

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mmg.manahub.core.data.remote.push.PushTokenRemoteDataSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background retry for a failed token de-registration. Calls [PushTokenRemoteDataSource.delete]
 * and retries (up to 3 attempts) while a network connection is available.
 */
@HiltWorker
class UnregisterPushTokenWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
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
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
