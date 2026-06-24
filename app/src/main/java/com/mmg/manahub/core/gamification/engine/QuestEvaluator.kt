package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.gamification.domain.QuestPeriod
import com.mmg.manahub.core.gamification.domain.QuestPeriodKeys
import com.mmg.manahub.core.gamification.domain.catalog.QuestCatalog
import com.mmg.manahub.core.gamification.domain.catalog.QuestTemplate
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.domain.model.QuestProgressDelta
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advances active quest progress for an event (ADR-002, Phase 2).
 *
 * For each catalog template registered for the event's class, it resolves the CURRENT period's
 * instance id (`{templateId}:{periodKey}` — daily key for DAILY templates, ISO-week key for WEEKLY),
 * loads it, and if the instance exists and is still `ACTIVE`, applies the template's `advance(event)`
 * increment (clamped to the target). An instance that reaches its target flips to `COMPLETED` (claim is
 * a separate, explicit step — [ClaimQuestRewardUseCase]).
 *
 * Pure period math comes from [QuestPeriodKeys]; [clock] + [zoneId] are injected so tests can pin the
 * period. The DAO upsert (INSERT-OR-IGNORE + @Update) never uses REPLACE.
 */
@Singleton
class QuestEvaluator @Inject constructor(
    private val dao: GamificationDao,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {

    /**
     * Applies [event] to every matching active quest instance for the current period. Returns one
     * [QuestProgressDelta] per instance that actually advanced (an empty list when nothing matched or
     * no increment applied).
     */
    suspend fun process(event: ProgressionEvent): List<QuestProgressDelta> {
        val templates = QuestCatalog.templatesByEventType[event::class] ?: return emptyList()
        val today = clock.now().toLocalDateTime(timeZone).date

        val deltas = mutableListOf<QuestProgressDelta>()
        for (template in templates) {
            val periodKey = periodKeyFor(template, today)
            val instanceId = "${template.id}:$periodKey"

            val instance = dao.getQuest(instanceId) ?: continue
            if (instance.status != STATUS_ACTIVE) continue

            val inc = template.advance(event)
            if (inc <= 0) continue

            val newProgress = (instance.progress + inc).coerceAtMost(instance.target)
            if (newProgress == instance.progress) continue // already capped at target

            val justCompleted = newProgress >= instance.target
            val newStatus = if (justCompleted) STATUS_COMPLETED else STATUS_ACTIVE

            dao.upsertQuest(instance.copy(progress = newProgress, status = newStatus))

            deltas.add(
                QuestProgressDelta(
                    instanceId = instanceId,
                    templateId = template.id,
                    titleRes = template.titleRes,
                    emoji = template.emoji,
                    newProgress = newProgress,
                    target = instance.target,
                    justCompleted = justCompleted,
                )
            )
        }
        return deltas
    }

    private fun periodKeyFor(template: QuestTemplate, today: LocalDate): String =
        when (template.period) {
            QuestPeriod.DAILY -> QuestPeriodKeys.dailyKey(today)
            QuestPeriod.WEEKLY -> QuestPeriodKeys.weeklyKey(today)
        }

    private companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_COMPLETED = "COMPLETED"
    }
}
