package com.mmg.manahub.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mmg.manahub.core.data.local.SyncPreferencesStore
import com.mmg.manahub.core.data.local.dao.StatsDao
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.model.CollectionColorAffinity
import com.mmg.manahub.core.model.PreferredCurrency
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Background worker that computes the current user's collection statistics from Room
 * and pushes them to Supabase via the `upsert_collection_stats` RPC.
 *
 * The stats snapshot is used by friends to view the collection overview on the
 * FriendStatsTab. The worker runs at most once every 23 hours (skip guard) and
 * retries up to 3 times with exponential backoff on transient failures.
 *
 * Scheduling is managed by [CollectionStatsSyncWorker.scheduleDailySync], which
 * uses [ExistingPeriodicWorkPolicy.KEEP] so that the period is never reset when the
 * app restarts.
 *
 * KMP migration — Hilt→Koin cutover batch 6: converted from `@HiltWorker`/`@AssistedInject` to a plain
 * [CoroutineWorker] resolved by Koin's `worker { }` DSL, registered in `core.sync.di.syncKoinModule`.
 * [authRepo]/[friendRepo] are native Koin singles in `coreBridgeKoinModule`, [syncPrefs] is a native
 * single in `gamificationEngineKoinModule`, and [statsDao] (Room, stays androidMain) is a NEW
 * forward-bridge this batch (see `syncKoinModule`'s KDoc).
 */
class CollectionStatsSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val authRepo: AuthRepository,
    private val syncPrefs: SyncPreferencesStore,
    private val statsDao: StatsDao,
    private val friendRepo: FriendRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : CoroutineWorker(appContext, workerParams) {

    companion object {

        /** Unique periodic work name used with [WorkManager.enqueueUniquePeriodicWork]. */
        const val WORK_NAME_PERIODIC = "stats_sync_daily"

        private const val TWENTY_THREE_HOURS_MS = 23L * 60L * 60L * 1000L
        private const val MAX_ATTEMPTS = 3

        fun periodicWorkRequest() =
            PeriodicWorkRequestBuilder<CollectionStatsSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

        fun scheduleDailySync(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest(),
            )
        }
    }

    override suspend fun doWork(): Result {
        val userId = authRepo.getCurrentUser()?.id ?: return Result.success()

        val lastSync = syncPrefs.getLastStatsSyncMillis(userId)
        if (System.currentTimeMillis() - lastSync < TWENTY_THREE_HOURS_MS) return Result.success()

        return try {
            val totals = statsDao.observeTotals(null, null, userId).first()
            val totalValueEur = statsDao.observeTotalValueEur(null, null, userId).first()
            val totalValueUsd = statsDao.observeTotalValueUsd(null, null, userId).first()
            val favouriteColor = computeFavouriteColor(userId)
            val mostValuableColor = computeMostValuableColor(userId)

            val upsertResult = friendRepo.upsertMyStats(
                uniqueCards = totals.uniqueCards,
                totalCards = totals.totalCards,
                totalValueEur = totalValueEur,
                totalValueUsd = totalValueUsd,
                favouriteColor = favouriteColor,
                mostValuableColor = mostValuableColor,
            )

            if (upsertResult.isSuccess) {
                // Re-verify the session is still the same user before writing the watermark,
                // guarding against a sign-out + sign-in that happened during the network call.
                val currentUserId = authRepo.getCurrentUser()?.id
                if (currentUserId == userId) {
                    syncPrefs.saveLastStatsSyncMillis(userId, System.currentTimeMillis())
                }
                Result.success()
            } else {
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    // Same rules as the owner's Profile (CollectionColorAffinity), so a friend sees the same colours.
    private suspend fun computeFavouriteColor(userId: String): String? {
        val rows = statsDao.observeCountByColorIdentity(null, null, userId).first()
        return CollectionColorAffinity.favouriteColorCode(
            CollectionColorAffinity.countByColor(rows.map { it.colorIdentity to it.count })
        )
    }

    /** Colour identity of the single most valuable card in the owner's currency, e.g. "UB" or "C". */
    private suspend fun computeMostValuableColor(userId: String): String? {
        val useEur = userPreferencesRepository.preferredCurrencyFlow.first() == PreferredCurrency.EUR
        val top = statsDao.observeMostValuableCards(limit = 1, useEur = useEur, colorFilter = null, setFilter = null, userId = userId)
            .first()
            .firstOrNull()
            ?: return null
        return CollectionColorAffinity.identityCodes(top.colorIdentity).joinToString("")
    }
}
