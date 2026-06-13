package com.mmg.manahub.core.gamification.data.repository

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.entity.AchievementProgressEntity
import com.mmg.manahub.core.data.local.entity.PlayerProgressionEntity
import com.mmg.manahub.core.data.local.entity.QuestInstanceEntity
import com.mmg.manahub.core.data.local.entity.StreakEntity
import com.mmg.manahub.core.gamification.domain.LevelCurve
import com.mmg.manahub.core.gamification.domain.QuestPeriod
import com.mmg.manahub.core.gamification.domain.QuestPeriodKeys
import com.mmg.manahub.core.gamification.domain.catalog.AchievementCatalog
import com.mmg.manahub.core.gamification.domain.catalog.AchievementDef
import com.mmg.manahub.core.gamification.domain.catalog.QuestCatalog
import com.mmg.manahub.core.gamification.domain.catalog.QuestTemplate
import com.mmg.manahub.core.gamification.domain.model.AchievementUiModel
import com.mmg.manahub.core.gamification.domain.model.PlayerProgression
import com.mmg.manahub.core.gamification.domain.model.QuestBoard
import com.mmg.manahub.core.gamification.domain.model.QuestUiModel
import com.mmg.manahub.core.gamification.domain.model.StreakUiModel
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import com.mmg.manahub.core.gamification.domain.usecase.ClaimQuestRewardUseCase
import com.mmg.manahub.core.gamification.domain.usecase.ClaimResult
import com.mmg.manahub.core.gamification.engine.StreakTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [GamificationRepository], backed by [GamificationDao].
 *
 * Maps the persisted [PlayerProgressionEntity] to a [PlayerProgression] domain model, splitting
 * `total_xp` into level + within-level progress via [LevelCurve]. A null entity (row not yet
 * seeded) maps to the level-1 / 0-XP default.
 */
@Singleton
class GamificationRepositoryImpl @Inject constructor(
    private val dao: GamificationDao,
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val claimQuestRewardUseCase: ClaimQuestRewardUseCase,
) : GamificationRepository {

    override fun observeProgression(): Flow<PlayerProgression> =
        dao.observeProgression().map { entity -> entity.toDomain() }

    override fun observeAchievements(): Flow<List<AchievementUiModel>> =
        dao.observeAchievements().map { rows ->
            val byId = rows.associateBy { it.achievementId }
            // Drive off the catalog (the source of truth) so locked/untracked achievements still show.
            AchievementCatalog.all.map { def -> def.toUiModel(byId[def.id]) }
        }

    override fun observePendingCelebrations(): Flow<List<AchievementUiModel>> =
        dao.observePendingCelebrations().map { rows ->
            // Map each pending row to its catalog def (oldest first, preserved by the DAO ORDER BY).
            // Defensive: a row whose def was removed from the catalog is skipped rather than crashing.
            rows.mapNotNull { row ->
                AchievementCatalog.byId(row.achievementId)?.toUiModel(row)
            }
        }

    override suspend fun markCelebrated(id: String) {
        dao.markCelebrated(id = id, celebratedAt = clock.millis())
    }

    override fun observeActiveQuests(): Flow<QuestBoard> =
        // Recompute the current period keys at collection time (cold `flow`), then observe the daily +
        // weekly instance flows for those keys and join them with the catalog into a [QuestBoard].
        flow {
            val today = clock.instant().atZone(zoneId).toLocalDate()
            val dailyKey = QuestPeriodKeys.dailyKey(today)
            val weeklyKey = QuestPeriodKeys.weeklyKey(today)
            emitAll(
                combine(
                    dao.observeQuestsForPeriod(dailyKey),
                    dao.observeQuestsForPeriod(weeklyKey),
                ) { dailyRows, weeklyRows ->
                    QuestBoard(
                        daily = dailyRows.toUiModels(QuestPeriod.DAILY),
                        weekly = weeklyRows.toUiModels(QuestPeriod.WEEKLY),
                    )
                }
            )
        }

    override fun observeDailyActivityStreak(): Flow<StreakUiModel> =
        dao.observeStreaks().map { rows ->
            rows.firstOrNull { it.type == StreakTracker.TYPE_DAILY_ACTIVITY }.toUiModel()
        }

    override suspend fun claimQuest(instanceId: String): ClaimResult =
        claimQuestRewardUseCase(instanceId)

    /**
     * Maps the period's instance rows to UI models, joining each with its catalog template metadata.
     * EXPIRED instances are dropped (not part of the active board); a row whose template was removed
     * from the catalog is skipped rather than crashing. Result ordered by [QuestCatalog.forPeriod] so
     * the board is stable across emissions regardless of DB row order.
     */
    private fun List<QuestInstanceEntity>.toUiModels(period: QuestPeriod): List<QuestUiModel> {
        val byTemplateId = associateBy { it.templateId }
        return QuestCatalog.forPeriod(period).mapNotNull { template ->
            val row = byTemplateId[template.id] ?: return@mapNotNull null
            if (row.status == STATUS_EXPIRED) return@mapNotNull null
            row.toUiModel(template)
        }
    }

    /** Joins a quest instance row with its catalog template into the UI model. */
    private fun QuestInstanceEntity.toUiModel(template: QuestTemplate): QuestUiModel =
        QuestUiModel(
            instanceId = id,
            templateId = templateId,
            titleRes = template.titleRes,
            descRes = template.descRes,
            emoji = template.emoji,
            period = template.period,
            weightClass = template.weightClass,
            progress = progress,
            target = target,
            status = status,
            xpReward = xpReward,
        )

    /** Maps the daily-activity streak row (or null) to its UI model, defaulting to a zeroed streak. */
    private fun StreakEntity?.toUiModel(): StreakUiModel =
        StreakUiModel(
            current = this?.current ?: 0,
            longest = this?.longest ?: 0,
            freezeTokens = this?.freezeTokens ?: StreakTracker.MAX_FREEZE_TOKENS,
        )

    /** Joins a catalog def with its (possibly absent) persisted progress row into the UI model. */
    private fun AchievementDef.toUiModel(progress: AchievementProgressEntity?): AchievementUiModel =
        AchievementUiModel(
            id = id,
            category = category,
            titleRes = titleRes,
            descRes = descRes,
            emoji = emoji,
            tierThresholds = tiers.map { it.threshold },
            currentValue = progress?.currentValue ?: 0,
            tierReached = progress?.tierReached ?: 0,
            maxTier = tiers.size,
            unlockedAt = progress?.unlockedAt,
            isSecret = isSecret,
        )

    private fun PlayerProgressionEntity?.toDomain(): PlayerProgression {
        val totalXp = this?.totalXp ?: 0L
        val (into, needed) = LevelCurve.xpIntoCurrentLevel(totalXp)
        return PlayerProgression(
            totalXp = totalXp,
            level = LevelCurve.levelForTotalXp(totalXp),
            xpIntoLevel = into,
            xpForNextLevel = needed,
            updatedAt = Instant.ofEpochMilli(this?.updatedAt ?: 0L),
        )
    }

    private companion object {
        const val STATUS_EXPIRED = "EXPIRED"
    }
}
