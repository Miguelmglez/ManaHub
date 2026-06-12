package com.mmg.manahub.core.gamification.domain.repository

import com.mmg.manahub.core.gamification.domain.model.AchievementUiModel
import com.mmg.manahub.core.gamification.domain.model.PlayerProgression
import kotlinx.coroutines.flow.Flow

/**
 * Read-side contract for gamification state consumed by the UI (Profile hero in Phase 0).
 *
 * Writes go exclusively through the engine ([com.mmg.manahub.core.gamification.engine.XpGranter]).
 * This repository only exposes reactive reads mapped to domain models.
 */
interface GamificationRepository {

    /**
     * Observes the player's progression. Emits a level-1 / 0-XP default until the singleton row
     * exists (seeded by the v39 migration, so in practice the row is always present).
     */
    fun observeProgression(): Flow<PlayerProgression>

    /**
     * Observes every achievement as a UI-ready model (Phase 1), joining the static
     * [com.mmg.manahub.core.gamification.domain.catalog.AchievementCatalog] with the persisted
     * `achievement_progress` rows. Locked-but-untracked achievements emit at value 0 / tier 0; secret
     * achievements expose [AchievementUiModel.isMasked] so the UI can render "???" until unlocked.
     */
    fun observeAchievements(): Flow<List<AchievementUiModel>>

    /**
     * Observes unlocked-but-not-yet-celebrated achievements as UI models, oldest unlock first
     * (Phase 1). Drives the global celebration queue: the host plays each in turn, then calls
     * [markCelebrated]. Backfilled unlocks (celebrated_at already stamped) never appear here, so
     * retroactive "you already had this" unlocks are not celebrated.
     */
    fun observePendingCelebrations(): Flow<List<AchievementUiModel>>

    /**
     * Marks an achievement's unlock celebration as shown so it drops out of
     * [observePendingCelebrations]. Called after the overlay finished (or was skipped) for [id].
     */
    suspend fun markCelebrated(id: String)
}
