package com.mmg.manahub.core.gamification.data.repository

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.entity.PlayerProgressionEntity
import com.mmg.manahub.core.gamification.domain.LevelCurve
import com.mmg.manahub.core.gamification.domain.model.PlayerProgression
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
) : GamificationRepository {

    override fun observeProgression(): Flow<PlayerProgression> =
        dao.observeProgression().map { entity -> entity.toDomain() }

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
