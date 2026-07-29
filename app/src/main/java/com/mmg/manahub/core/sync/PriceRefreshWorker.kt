package com.mmg.manahub.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.usecase.collection.RefreshCollectionPricesUseCase
import com.mmg.manahub.core.util.recordNonFatal
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * KMP migration — Hilt→Koin cutover batch 6: converted from `@HiltWorker`/`@AssistedInject` to a plain
 * [CoroutineWorker] resolved by Koin's `worker { }` DSL, registered in `core.sync.di.syncKoinModule`.
 * [refreshPricesUseCase] is a native Koin single in `SharedDomainKoinModule` (KMP migration batch 2);
 * [userPreferencesDataStore] is bridged in `coreBridgeKoinModule`.
 *
 * Backend & Performance Optimization plan, WS1+WS3 Part B item 7 (2026-07-28): [refreshPricesUseCase]
 * now processes STALE ids in bounded slices and can return early with
 * [RefreshCollectionPricesUseCase.Result.Capped] (see its class KDoc for why there is no persisted
 * cursor). This worker only claims the 23h watermark on a full [RefreshCollectionPricesUseCase.Result.Success]
 * pass; a [RefreshCollectionPricesUseCase.Result.Capped] result instead self-enqueues a one-time
 * follow-up (via `WorkManager.getInstance(applicationContext)` — this worker has no Koin-bridged
 * `WorkManager` of its own, and the app-wide singleton accessor is the standard way to reach it from
 * inside a `CoroutineWorker`) so a very large collection finishes soon rather than waiting for
 * tomorrow's periodic tick, without monopolising the Scryfall budget in one run.
 */
class PriceRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val refreshPricesUseCase: RefreshCollectionPricesUseCase,
    private val userPreferencesDataStore: UserPreferencesDataStore,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME_PERIODIC = "price_refresh_daily"

        /** One-time follow-up scheduled when a run is capped before finishing (see class KDoc). */
        const val WORK_NAME_FOLLOW_UP = "price_refresh_follow_up"

        fun periodicWorkRequest() =
            PeriodicWorkRequestBuilder<PriceRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

        fun followUpWorkRequest() =
            OneTimeWorkRequestBuilder<PriceRefreshWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
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

        // WorkManager may fire slightly early — skip if refreshed within last 23 hours. A capped
        // run never advances this watermark (below), so a follow-up chain always re-enters here
        // and correctly proceeds past this guard.
        val elapsed = System.currentTimeMillis() - (lastRefresh ?: 0L)
        if (elapsed < 23 * 60 * 60 * 1000L) return Result.success()

        return try {
            var completed = false
            var capped = false
            refreshPricesUseCase.invoke().collect { result ->
                when (result) {
                    is RefreshCollectionPricesUseCase.Result.Success -> {
                        // A full pass — every id stale at the start of this run is now fresh.
                        // Only NOW is the daily watermark claimed.
                        userPreferencesDataStore.saveLastPriceRefresh(System.currentTimeMillis())
                        completed = true
                        // WS7 telemetry (2026-07-29): proves the sliced/stale-only refresh actually
                        // completed and how much it did, without needing to eyeball Room.
                        FirebaseCrashlytics.getInstance().apply {
                            log("price_refresh_worker_completed")
                            setCustomKey("price_refresh_updated_count", result.updatedCount)
                            setCustomKey("price_refresh_not_found_count", result.notFoundCount)
                            setCustomKey("price_refresh_remaining_stale_count", 0)
                        }
                    }
                    is RefreshCollectionPricesUseCase.Result.Capped -> {
                        // Some ids remain stale — do NOT claim the watermark. The already-written
                        // slices are fresh in Room now, so the follow-up run below naturally picks
                        // up only what's left (see the use case's "no persisted cursor" KDoc).
                        capped = true
                        FirebaseCrashlytics.getInstance().apply {
                            log("price_refresh_worker_capped")
                            setCustomKey("price_refresh_updated_count", result.updatedCount)
                            setCustomKey("price_refresh_not_found_count", result.notFoundCount)
                            setCustomKey("price_refresh_remaining_stale_count", result.remainingStaleCount)
                        }
                    }
                    is RefreshCollectionPricesUseCase.Result.Error -> {
                        // Previously swallowed by a bare `else -> {}` — Result.Error had ZERO signal.
                        // The use case's own message is dev-controlled failure text (network/Room
                        // exception message), but per the project's PII rule it is never logged
                        // verbatim — only its length, alongside a dev-authored non-fatal message.
                        recordNonFatal(
                            "price_refresh_worker_error",
                            RuntimeException("price_refresh_worker_error len=${result.message.length}"),
                        )
                    }
                    else -> {}
                }
            }

            if (capped) {
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    WORK_NAME_FOLLOW_UP,
                    ExistingWorkPolicy.KEEP,
                    followUpWorkRequest(),
                )
            }

            if (completed || capped) Result.success()
            else if (runAttemptCount < 3) Result.retry()
            else Result.failure()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
