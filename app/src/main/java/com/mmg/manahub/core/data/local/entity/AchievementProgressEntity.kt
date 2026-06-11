package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stateful progress for a single achievement (Phase 1).
 *
 * `unlocked_at` and `celebrated_at` are deliberately SEPARATE nullable columns: an
 * achievement can be unlocked silently during the Family-A backfill (`unlocked_at` set,
 * `celebrated_at` null) and have its celebration shown later. This fixes the old
 * `NOW`-on-recompute bug where every recompute re-fired the celebration (ADR-002 §8).
 *
 * `achievement_id` is a code-side stable catalog id (String) — NEVER renamed once shipped.
 */
@Entity(tableName = "achievement_progress")
data class AchievementProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "achievement_id")
    val achievementId: String,

    /** Current accumulated progress value toward the next tier. */
    @ColumnInfo(name = "current_value")
    val currentValue: Int,

    /** Highest tier index reached (0 = none). */
    @ColumnInfo(name = "tier_reached")
    val tierReached: Int,

    /** Epoch-millis of the real unlock, or null if not yet unlocked. */
    @ColumnInfo(name = "unlocked_at")
    val unlockedAt: Long?,

    /** Epoch-millis the unlock celebration was shown, or null if not yet celebrated. */
    @ColumnInfo(name = "celebrated_at")
    val celebratedAt: Long?,
)
