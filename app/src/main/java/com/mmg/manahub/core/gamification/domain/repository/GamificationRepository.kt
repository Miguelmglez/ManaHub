package com.mmg.manahub.core.gamification.domain.repository

import com.mmg.manahub.core.gamification.domain.model.AchievementUiModel
import com.mmg.manahub.core.gamification.domain.model.EquippedCosmetics
import com.mmg.manahub.core.gamification.domain.model.PlayerProgression
import com.mmg.manahub.core.gamification.domain.model.QuestBoard
import com.mmg.manahub.core.gamification.domain.model.RewardsBoard
import com.mmg.manahub.core.gamification.domain.model.StreakUiModel
import com.mmg.manahub.core.gamification.domain.usecase.ClaimResult
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

    /**
     * Observes the current period's quest board (Phase 2): today's daily quests + this week's weekly
     * quests, joined with their catalog metadata. The current period keys are recomputed at collection
     * time from the device clock/zone, so the board reflects the day/week the collector is observing
     * in. EXPIRED instances are excluded; ACTIVE / COMPLETED / CLAIMED are included.
     */
    fun observeActiveQuests(): Flow<QuestBoard>

    /**
     * Observes the daily-activity streak (Phase 2). Emits a zeroed default
     * (`current = 0, longest = 0, freezeTokens = MAX_FREEZE_TOKENS`) until the streak row exists.
     */
    fun observeDailyActivityStreak(): Flow<StreakUiModel>

    /**
     * Claims a COMPLETED quest's XP reward (Phase 2). Delegates to the idempotent claim use case;
     * a double-claim never double-grants. Returns the outcome for the UI to surface.
     */
    suspend fun claimQuest(instanceId: String): ClaimResult

    // ── Rewards / cosmetics (Phase 3, ADR-002 §10) ────────────────────────────

    /**
     * Observes the Rewards board (Phase 3): every cosmetic in
     * [com.mmg.manahub.core.gamification.domain.catalog.UnlockableCatalog] joined with the player's
     * `entitlements` (ownership) and DataStore-equipped selection. Locked items are still emitted
     * (driven off the catalog) so the UI can show what's available with an "how to unlock" hint.
     */
    fun observeRewards(): Flow<RewardsBoard>

    /**
     * Observes the player's equipped cosmetics (Phase 3). Chunk B's Profile hero renders the equipped
     * title/badges/frame/ring from this. Passthrough to DataStore.
     */
    fun observeEquippedCosmetics(): Flow<EquippedCosmetics>

    /**
     * Equips the TITLE [id], or clears the slot when null. GUARDED: an [id] the player does not own is
     * silently ignored (no-op), so the UI can never equip an unearned cosmetic even if it races the
     * entitlement state.
     */
    suspend fun equipTitle(id: String?)

    /**
     * Equips up to [EquippedCosmetics.MAX_EQUIPPED_BADGES] BADGE [ids], in order. Unowned ids are
     * filtered out; an empty result clears the slot.
     */
    suspend fun equipBadges(ids: List<String>)

    /** Equips the AVATAR_FRAME [id], or clears the slot when null. Unowned [id] is a silent no-op. */
    suspend fun equipAvatarFrame(id: String?)

    /** Equips the LEVEL_RING_STYLE [id], or clears the slot when null. Unowned [id] is a silent no-op. */
    suspend fun equipLevelRingStyle(id: String?)
}
