package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ownership of an unlockable cosmetic (Phase 3).
 *
 * Entitlements merge as a UNION across devices (ADR-002 §11) — once granted, always granted.
 * Equipped selection lives in DataStore, not here. `unlockable_id` is a code-side stable
 * catalog id (String).
 */
@Entity(tableName = "entitlements")
data class EntitlementEntity(
    @PrimaryKey
    @ColumnInfo(name = "unlockable_id")
    val unlockableId: String,

    /** Epoch-millis when the unlockable was granted. */
    @ColumnInfo(name = "unlocked_at")
    val unlockedAt: Long,

    /** How it was granted: "LEVEL_UP" / "ACHIEVEMENT" / etc. */
    @ColumnInfo(name = "source")
    val source: String,
)
