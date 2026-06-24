package com.mmg.manahub.core.gamification.domain.model

import com.mmg.manahub.core.gamification.domain.QuestPeriod
import com.mmg.manahub.core.gamification.domain.QuestWeightClass

/**
 * UI-ready view of one quest instance (ADR-002, Phase 2).
 *
 * Produced by `GamificationRepository.observeActiveQuests()` by joining each persisted
 * `quest_instances` row with its [com.mmg.manahub.core.gamification.domain.catalog.QuestTemplate]
 * metadata. Titles/descriptions are string resources (resolved in the Composable) so the domain layer
 * stays `android.content.Context`-free. Chunk B's Quests tab + Home widgets consume this directly.
 *
 * @param instanceId persisted instance id (`{templateId}:{periodKey}`).
 * @param templateId catalog template id.
 * @param titleRes / descRes English string resources.
 * @param emoji glyph.
 * @param period DAILY / WEEKLY.
 * @param weightClass difficulty band (for grouping/visual treatment).
 * @param progress current progress.
 * @param target completion target.
 * @param status lifecycle status: "ACTIVE" / "COMPLETED" / "CLAIMED" (EXPIRED rows are excluded).
 * @param xpReward XP granted on claim.
 */
data class QuestUiModel(
    val instanceId: String,
    val templateId: String,
    val titleRes: Int,
    val descRes: Int,
    val emoji: String,
    val period: QuestPeriod,
    val weightClass: QuestWeightClass,
    val progress: Int,
    val target: Int,
    val status: String,
    val xpReward: Int,
) {
    /** True only when the quest is COMPLETED and not yet claimed (the claim CTA should show). */
    val isClaimable: Boolean get() = status == STATUS_COMPLETED

    /** True once the reward has been claimed. */
    val isClaimed: Boolean get() = status == STATUS_CLAIMED

    /** Progress toward [target], clamped to [0f, 1f]. */
    val progressFraction: Float
        get() = if (target <= 0) 0f else (progress.toFloat() / target).coerceIn(0f, 1f)

    private companion object {
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_CLAIMED = "CLAIMED"
    }
}

/**
 * The active quest board: the current daily + weekly quests (ADR-002, Phase 2).
 *
 * EXPIRED instances are excluded; ACTIVE / COMPLETED / CLAIMED are included so the UI can show
 * in-progress, claimable, and already-claimed quests for the current period.
 */
data class QuestBoard(
    val daily: List<QuestUiModel>,
    val weekly: List<QuestUiModel>,
) {
    companion object {
        /** An empty board (no quests generated yet). */
        val empty: QuestBoard = QuestBoard(daily = emptyList(), weekly = emptyList())
    }
}

/**
 * UI-ready view of a streak counter (ADR-002, Phase 2).
 *
 * Defaults to a zeroed streak with [com.mmg.manahub.core.gamification.engine.StreakTracker.MAX_FREEZE_TOKENS]
 * tokens when no row exists yet, so the UI always has a sensible value.
 *
 * @param current current consecutive-day count.
 * @param longest best-ever count.
 * @param freezeTokens freeze tokens banked to protect the streak.
 */
data class StreakUiModel(
    val current: Int,
    val longest: Int,
    val freezeTokens: Int,
)
