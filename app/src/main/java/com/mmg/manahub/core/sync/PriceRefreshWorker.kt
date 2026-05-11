package com.mmg.manahub.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.usecase.collection.RefreshCollectionPricesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class PriceRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val refreshPricesUseCase: RefreshCollectionPricesUseCase,
    private val userPreferencesDataStore: UserPreferencesDataStore,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME_PERIODIC = "price_refresh_daily"

        fun periodicWorkRequest() =
            PeriodicWorkRequestBuilder<PriceRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

        fun scheduleDailyRefresh(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest(),
            )
        }
    }

    override suspend fun doWork(): Result {
        val lastRefresh = userPreferencesDataStore.lastPriceRefreshFlow.first()

        // WorkManager may fire slightly early — skip if refreshed within last 23 hours
        val elapsed = System.currentTimeMillis() - (lastRefresh ?: 0L)
        if (elapsed < 23 * 60 * 60 * 1000L) return Result.success()

        return try {
            var succeeded = false
            refreshPricesUseCase.invoke().collect { result ->
                if (result is RefreshCollectionPricesUseCase.Result.Success) {
                    userPreferencesDataStore.saveLastPriceRefresh(System.currentTimeMillis())
                    succeeded = true
                }
            }
            if (succeeded) Result.success()
            else if (runAttemptCount < 3) Result.retry()
            else Result.failure()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
