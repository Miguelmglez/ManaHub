package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton row (id == [SINGLETON_ID]) holding the player's denormalized progression.
 *
 * `level` is denormalized from `total_xp` via the level curve so the Profile hero can render
 * without recomputation; it is kept consistent by [com.mmg.manahub.core.gamification.engine.XpGranter]
 * on every grant. `total_xp` is the source of truth and is monotonic (ADR-002 §11).
 *
 * Time is stored as epoch-millis (`Long`) primitive to avoid touching the shared RoomConverters.
 */
@Entity(tableName = "player_progression")
data class PlayerProgressionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SINGLETON_ID,

    /** Lifetime XP. Monotonic source of truth. */
    @ColumnInfo(name = "total_xp")
    val totalXp: Long,

    /** Denormalized level, derived from [totalXp]. */
    @ColumnInfo(name = "level")
    val level: Int,

    /** Epoch-millis of the last progression change. */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    companion object {
        /** The only valid primary key — there is exactly one progression row. */
        const val SINGLETON_ID: Int = 1
    }
}
