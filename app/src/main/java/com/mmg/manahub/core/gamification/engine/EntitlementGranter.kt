package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.entity.EntitlementEntity
import com.mmg.manahub.core.gamification.domain.LevelCurve
import com.mmg.manahub.core.gamification.domain.catalog.Unlockable
import com.mmg.manahub.core.gamification.domain.catalog.UnlockableCatalog
import com.mmg.manahub.core.gamification.domain.catalog.UnlockableId
import com.mmg.manahub.core.gamification.domain.model.ProgressionOutcome
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Grants unlockable-cosmetic entitlements when the player's state satisfies a catalog rule (ADR-002
 * §10, Phase 3).
 *
 * Two entry points:
 * - [grant] runs per-event from [GamificationEngineImpl.process] (after the XP/achievement/quest/streak
 *   evaluators) using the [ProgressionOutcome] just produced — the new level and the achievements
 *   unlocked *this event*. Cheap and incremental.
 * - [reconcileAll] runs once per app launch from `ManaHubApp.onCreate` as a RETROACTIVE catch-up: it
 *   evaluates every catalog rule against the full current state (current level + ALL unlocked
 *   achievements) so existing players (already L20, already unlocked COLLECTOR, …) receive the
 *   cosmetics introduced in this release.
 *
 * Both paths are IDEMPOTENT: each insert goes through [GamificationDao.insertEntitlementIfAbsent]
 * (INSERT OR IGNORE → rowId -1 when already owned), and a `hasEntitlement` pre-check short-circuits
 * before building the row. Per-item failures are isolated so one bad row never aborts the batch; the
 * engine call site additionally wraps [grant] in `runCatching` so this can never crash the collector.
 *
 * [clock] is injected so the `unlocked_at` stamp is testable.
 */
@Singleton
class EntitlementGranter @Inject constructor(
    private val dao: GamificationDao,
    private val clock: Clock,
) {

    /**
     * Grants any newly-satisfied entitlements implied by [outcome] (per-event path).
     *
     * Uses [ProgressionOutcome.newLevel] when present (else derives the level from the persisted total)
     * and the achievement ids unlocked in this outcome. Only LEVEL rules at/under the current level and
     * ACHIEVEMENT rules for the just-unlocked ids can fire here; the full catch-up is [reconcileAll].
     *
     * @return the ids actually granted by this call (empty when nothing new was earned).
     */
    suspend fun grant(outcome: ProgressionOutcome): List<UnlockableId> {
        // Nothing in this outcome can newly satisfy a rule unless it leveled up or unlocked something.
        if (!outcome.leveledUp && outcome.achievementUnlocks.isEmpty()) return emptyList()

        val currentLevel = outcome.newLevel ?: currentLevelFromDao()
        val justUnlockedAchievementIds = outcome.achievementUnlocks.map { it.id }.toSet()

        return grantSatisfied(
            currentLevel = currentLevel,
            unlockedAchievementIds = justUnlockedAchievementIds,
        )
    }

    /**
     * Retroactive catch-up: grants EVERY catalog entitlement the player currently qualifies for, based
     * on the full state (current level + all unlocked achievements). Idempotent — only missing rows are
     * inserted, so it is safe to call on every launch.
     *
     * @return the ids newly granted by this reconcile (empty when the player was already up to date).
     */
    suspend fun reconcileAll(): List<UnlockableId> {
        val currentLevel = currentLevelFromDao()
        val unlockedAchievementIds = dao.getUnlockedAchievementIds().toSet()
        return grantSatisfied(
            currentLevel = currentLevel,
            unlockedAchievementIds = unlockedAchievementIds,
        )
    }

    /**
     * Inserts an entitlement for every catalog item whose rule is satisfied by the given state and that
     * the player does not already own. Per-item isolated so one failure cannot abort the rest.
     */
    private suspend fun grantSatisfied(
        currentLevel: Int,
        unlockedAchievementIds: Set<String>,
    ): List<UnlockableId> {
        val nowMillis = clock.now().toEpochMilliseconds()
        val granted = mutableListOf<UnlockableId>()

        for (unlockable in UnlockableCatalog.all) {
            val satisfied = unlockable.unlockRule.isSatisfied(currentLevel, unlockedAchievementIds)
            if (!satisfied) continue

            runCatching {
                if (dao.hasEntitlement(unlockable.id.value)) return@runCatching
                val rowId = dao.insertEntitlementIfAbsent(
                    EntitlementEntity(
                        unlockableId = unlockable.id.value,
                        unlockedAt = nowMillis,
                        source = unlockable.sourceLabel(),
                    )
                )
                // rowId == -1 means a concurrent insert already added it → not a new grant.
                if (rowId != -1L) granted += unlockable.id
            }
        }
        return granted
    }

    /** Reads the player's current level from the persisted total (level-1 default when no row yet). */
    private suspend fun currentLevelFromDao(): Int =
        LevelCurve.levelForTotalXp(dao.getProgression()?.totalXp ?: 0L)

    /** The `source` string stamped on the entitlement, derived from the rule kind. */
    private fun Unlockable.sourceLabel(): String = when (unlockRule) {
        is com.mmg.manahub.core.gamification.domain.catalog.UnlockRule.LevelAtLeast -> SOURCE_LEVEL_UP
        is com.mmg.manahub.core.gamification.domain.catalog.UnlockRule.AchievementUnlocked -> SOURCE_ACHIEVEMENT
    }

    private companion object {
        const val SOURCE_LEVEL_UP = "LEVEL_UP"
        const val SOURCE_ACHIEVEMENT = "ACHIEVEMENT"
    }
}
