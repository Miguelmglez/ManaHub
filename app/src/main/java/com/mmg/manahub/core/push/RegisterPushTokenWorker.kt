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
 * Background retry for a failed token registration. Calls [PushTokenRemoteDataSource.upsert]
 * and retries (up to 3 attempts) while a network connection is available.
 *
 * KMP migration — Hilt→Koin cutover batch 6: converted from `@HiltWorker`/`@AssistedInject` to a plain
 * [CoroutineWorker] resolved by Koin's `worker { }` DSL, registered in `core.push.di.pushKoinModule`
 * ([dataSource] is forward-bridged there from the same Hilt-built singleton
 * [PushTokenRepositoryImpl][com.mmg.manahub.core.data.repository.PushTokenRepositoryImpl] still uses).
 */
class RegisterPushTokenWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val dataSource: PushTokenRemoteDataSource,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val token = inputData.getString(KEY_TOKEN) ?: return Result.failure()
        val locale = inputData.getString(KEY_LOCALE) ?: return Result.failure()
        return try {
            dataSource.upsert(token, locale)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "push-register"
        private const val KEY_TOKEN = "token"
        private const val KEY_LOCALE = "locale"

        fun enqueue(workManager: WorkManager, token: String, locale: String) {
            val request = OneTimeWorkRequestBuilder<RegisterPushTokenWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(
                    Data.Builder()
                        .putString(KEY_TOKEN, token)
                        .putString(KEY_LOCALE, locale)
                        .build()
                )
                .build()
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
