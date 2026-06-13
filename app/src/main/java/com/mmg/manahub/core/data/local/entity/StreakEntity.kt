package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A streak counter keyed by its type (Phase 2).
 *
 * Streaks use freeze tokens, never punishment (ADR-002 §Context). `last_active_date` is an
 * ISO `yyyy-MM-dd` local-date string (primitive, avoids touching shared RoomConverters).
 *
 * `type` is a code-side stable identifier (e.g. "daily_activity", "win") — never renamed.
 */
@Entity(tableName = "streaks")
data class StreakEntity(
    @PrimaryKey
    @ColumnInfo(name = "type")
    val type: String,

    /** Current consecutive count. */
    @ColumnInfo(name = "current")
    val current: Int,

    /** Best-ever count for this streak type. */
    @ColumnInfo(name = "longest")
    val longest: Int,

    /** Last active local day, `yyyy-MM-dd`. */
    @ColumnInfo(name = "last_active_date")
    val lastActiveDate: String,

    /** Number of freeze tokens available to protect the streak. */
    @ColumnInfo(name = "freeze_tokens")
    val freezeTokens: Int,
)
