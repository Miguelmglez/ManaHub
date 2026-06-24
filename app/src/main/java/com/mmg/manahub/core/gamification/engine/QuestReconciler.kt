package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.gamification.domain.QuestGenerator
import com.mmg.manahub.core.gamification.domain.QuestPeriod
import com.mmg.manahub.core.gamification.domain.QuestPeriodKeys
import com.mmg.manahub.core.gamification.domain.QuestStableIdProvider
import com.mmg.manahub.core.gamification.domain.usecase.ClaimQuestRewardUseCase
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rolls quests over between periods (ADR-002, Phase 2).
 *
 * Two responsibilities, both idempotent so [reconcile] is safe to call repeatedly (it runs on every
 * app start AND from [com.mmg.manahub.core.gamification.data.sync.QuestRotationWorker]):
 *
 * 1. **Settle stale instances.** Anything past its `expires_at` that is still ACTIVE becomes EXPIRED;
 *    anything still COMPLETED is AUTO-CLAIMED through [ClaimQuestRewardUseCase] so a user who finished a
 *    quest but never tapped "claim" before the period rolled over does not lose the XP. (The claim is
 *    ledger-idempotent, so auto-claim + a racing manual claim can never double-grant.)
 * 2. **Generate the current period if missing.** For both DAILY and WEEKLY, if no instance exists for
 *    the current period key it deterministically generates the period's quests ([QuestGenerator]),
 *    de-prioritising the immediately-previous period's templates so the same quest rarely repeats.
 *
 * All time windows derive from the injected [clock] + [zoneId] — never `LocalDate.now()` — so the
 * roll-over boundary is testable and matches the device's local midnight / ISO week.
 */
@Singleton
class QuestReconciler @Inject constructor(
    private val dao: GamificationDao,
    private val stableIdProvider: QuestStableIdProvider,
    private val claimQuestRewardUseCase: ClaimQuestRewardUseCase,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {

    /** Settles stale quests, then ensures the current daily + weekly periods are generated. */
    suspend fun reconcile() {
        val now = clock.now().toEpochMilliseconds()
        val today = clock.now().toLocalDateTime(timeZone).date

        settleStale(now)
        ensurePeriodGenerated(QuestPeriod.DAILY, today)
        ensurePeriodGenerated(QuestPeriod.WEEKLY, today)
    }

    /**
     * EXPIRE every stale ACTIVE instance and AUTO-CLAIM every stale COMPLETED one. CLAIMED/EXPIRED rows
     * are already excluded by the query.
     */
    private suspend fun settleStale(now: Long) {
        for (instance in dao.getStaleQuests(now)) {
            when (instance.status) {
                STATUS_COMPLETED -> claimQuestRewardUseCase(instance.id) // ledger-idempotent
                STATUS_ACTIVE -> dao.upsertQuest(instance.copy(status = STATUS_EXPIRED))
            }
        }
    }

    /**
     * Generates the [period]'s quests for the period containing [today] if (and only if) none exist
     * yet. The no-repeat rule reads the previous period's template ids; the period's expiry is computed
     * from [zoneId].
     */
    private suspend fun ensurePeriodGenerated(period: QuestPeriod, today: LocalDate) {
        val periodKey = currentKey(period, today)
        if (dao.countQuestsForPeriod(periodKey) > 0) return

        val previousKey = previousKey(period, today)
        val previousTemplateIds = dao.getQuestsForPeriod(previousKey)
            .map { it.templateId }
            .toSet()

        val expiresAt = expiresAt(period, today)
        val instances = QuestGenerator.generateInstances(
            stableId = stableIdProvider.stableId(),
            period = period,
            periodKey = periodKey,
            expiresAt = expiresAt,
            previousPeriodTemplateIds = previousTemplateIds,
        )
        instances.forEach { dao.upsertQuest(it) }
    }

    private fun currentKey(period: QuestPeriod, today: LocalDate): String = when (period) {
        QuestPeriod.DAILY -> QuestPeriodKeys.dailyKey(today)
        QuestPeriod.WEEKLY -> QuestPeriodKeys.weeklyKey(today)
    }

    private fun previousKey(period: QuestPeriod, today: LocalDate): String = when (period) {
        QuestPeriod.DAILY -> QuestPeriodKeys.previousDailyKey(today)
        QuestPeriod.WEEKLY -> QuestPeriodKeys.previousWeeklyKey(today)
    }

    private fun expiresAt(period: QuestPeriod, today: LocalDate): Long = when (period) {
        QuestPeriod.DAILY -> QuestPeriodKeys.dailyExpiresAt(today, timeZone)
        QuestPeriod.WEEKLY -> QuestPeriodKeys.weeklyExpiresAt(today, timeZone)
    }

    private companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_EXPIRED = "EXPIRED"
    }
}
