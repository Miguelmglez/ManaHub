package com.mmg.manahub.core.gamification.domain.repository

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
}
