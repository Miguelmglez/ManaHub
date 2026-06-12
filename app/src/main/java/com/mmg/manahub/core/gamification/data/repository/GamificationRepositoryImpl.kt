package com.mmg.manahub.core.gamification.data.repository

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.entity.AchievementProgressEntity
import com.mmg.manahub.core.data.local.entity.PlayerProgressionEntity
import com.mmg.manahub.core.gamification.domain.LevelCurve
import com.mmg.manahub.core.gamification.domain.catalog.AchievementCatalog
import com.mmg.manahub.core.gamification.domain.catalog.AchievementDef
import com.mmg.manahub.core.gamification.domain.model.AchievementUiModel
import com.mmg.manahub.core.gamification.domain.model.PlayerProgression
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
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
}
