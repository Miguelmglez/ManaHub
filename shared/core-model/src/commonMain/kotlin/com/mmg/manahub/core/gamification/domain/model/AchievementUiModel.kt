package com.mmg.manahub.core.gamification.domain.model

/**
 * The rich, UI-ready view of one achievement (ADR-002, Phase 1).
 *
 * Produced by `GamificationRepository.observeAchievements()` by joining each
 * [com.mmg.manahub.core.gamification.domain.catalog.AchievementDef] with its persisted
 * `achievement_progress` row. Chunk B's Achievements tab + celebration overlay consume this directly.
 *
 * Titles/descriptions are exposed as inline English strings (no Android string resources) so the
 * domain layer stays platform-agnostic.
 *
 * @param id stable catalog id.
 * @param category catalog grouping.
 * @param title / description inline English text.
 * @param emoji glyph.
 * @param tierThresholds ascending thresholds (one per tier).
 * @param currentValue current progress value toward the next tier.
 * @param tierReached highest tier index reached (0 = none).
 * @param maxTier total number of tiers.
 * @param unlockedAt real epoch-millis of the first unlock, or null if still locked.
 * @param isSecret whether this is a hidden achievement (mask while locked).
 */
data class AchievementUiModel(
    val id: String,
    val category: AchievementCategory,
    val title: String,
    val description: String,
    val emoji: String,
    val tierThresholds: List<Int>,
    val currentValue: Int,
    val tierReached: Int,
    val maxTier: Int,
    val unlockedAt: Long?,
    val isSecret: Boolean,
) {
    val isUnlocked: Boolean get() = unlockedAt != null

    /** A secret achievement that should be visually masked as "???" (secret AND still locked). */
    val isMasked: Boolean get() = isSecret && !isUnlocked

    /** Progress toward the next (or final) tier, clamped to [0f, 1f]. */
    val progressFraction: Float
        get() {
            val target = tierThresholds.getOrNull(tierReached) ?: tierThresholds.lastOrNull() ?: 0
            if (target <= 0) return if (isUnlocked) 1f else 0f
            return (currentValue.toFloat() / target).coerceIn(0f, 1f)
        }
}
