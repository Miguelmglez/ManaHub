package com.mmg.manahub.core.gamification.domain.model

/**
 * A single achievement-tier unlock produced while processing one event (ADR-002, Phase 1).
 *
 * Surfaced on [ProgressionOutcome.achievementUnlocks] so Chunk B's GameResult progression strip can
 * render "Achievement unlocked!" badges. Carries only display data + the tier's XP reward (the XP was
 * already granted to the ledger by the evaluator; this is purely informational for the UI).
 *
 * @param id stable catalog id.
 * @param title English title text.
 * @param emoji glyph.
 * @param tier the tier index (1-based) that was just unlocked.
 * @param xpReward XP that tier granted.
 */
data class AchievementUnlock(
    val id: String,
    val title: String,
    val emoji: String,
    val tier: Int,
    val xpReward: Int,
)
