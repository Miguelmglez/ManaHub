package com.mmg.manahub.core.gamification.domain.model

import java.time.Instant

/**
 * The player's current progression state, derived from the singleton
 * `player_progression` row + [com.mmg.manahub.core.gamification.domain.LevelCurve].
 *
 * @param totalXp lifetime XP (monotonic; never decreases — see ADR-002 §11).
 * @param level current level, derived from [totalXp] via the level curve.
 * @param xpIntoLevel XP earned into the current level.
 * @param xpForNextLevel total XP span of the current level (XP needed to reach the next).
 * @param updatedAt when the progression last changed.
 */
data class PlayerProgression(
    val totalXp: Long,
    val level: Int,
    val xpIntoLevel: Long,
    val xpForNextLevel: Long,
    val updatedAt: Instant,
)
