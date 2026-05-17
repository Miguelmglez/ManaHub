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
import com.mmg.manahub.core.data.local.SyncPreferencesStore
import com.mmg.manahub.core.data.local.dao.StatsDao
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
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
 */
@HiltWorker
class CollectionStatsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val authRepo: AuthRepository,
    private val syncPrefs: SyncPreferencesStore,
    private val statsDao: StatsDao,
    private val friendRepo: FriendRepository,
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

            val upsertResult = friendRepo.upsertMyStats(
                uniqueCards = totals.uniqueCards,
                totalCards = totals.totalCards,
                totalValueEur = totalValueEur,
                totalValueUsd = totalValueUsd,
                favouriteColor = favouriteColor,
                mostValuableColor = null,
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

    /**
     * Returns the single-letter color code of the most common color in the user's collection,
     * or null when the collection is empty or contains only multi-color/colorless cards.
     *
     * color_identity is stored as a JSON array (e.g. '["W","U"]'). A card is attributed to a
     * single color only when the array contains exactly one entry; multi-color cards are skipped.
     */
    private suspend fun computeFavouriteColor(userId: String): String? {
        val rows = statsDao.observeCountByColorIdentity(null, null, userId).first()
        // Rows with colorIdentity like '["W"]' are mono-color. Extract the letter inside.
        return rows
            .mapNotNull { row ->
                val trimmed = row.colorIdentity.trim()
                // Match single-element JSON arrays: ["X"]
                val match = Regex("^\\[\"([WUBRG])\"]$").find(trimmed)
                if (match != null) match.groupValues[1] to row.count else null
            }
            .maxByOrNull { it.second }
            ?.first
    }
}
