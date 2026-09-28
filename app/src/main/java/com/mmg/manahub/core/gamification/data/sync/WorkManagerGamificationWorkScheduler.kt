package com.mmg.manahub.core.gamification.data.sync

import androidx.work.WorkManager
import com.mmg.manahub.core.gamification.domain.GamificationWorkScheduler

/** [GamificationWorkScheduler] backed by WorkManager unique work names. */
class WorkManagerGamificationWorkScheduler(
    private val workManager: WorkManager,
) : GamificationWorkScheduler {

    override fun scheduleLocalWork() = QuestRotationWorker.scheduleDaily(workManager)

    override fun scheduleSync() = GamificationSyncWorker.schedulePeriodicSync(workManager)

    override fun cancelSync() {
        workManager.cancelUniqueWork(GamificationSyncWorker.WORK_NAME_PERIODIC)
        workManager.cancelUniqueWork(GamificationSyncWorker.WORK_NAME_ONE_TIME)
    }

    override fun cancelAll() {
        workManager.cancelUniqueWork(QuestRotationWorker.WORK_NAME)
        cancelSync()
    }
}
