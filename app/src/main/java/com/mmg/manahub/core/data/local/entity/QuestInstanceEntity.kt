package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single quest instance for a period (Phase 2).
 *
 * The primary key is `{templateId}:{periodKey}` so the same template generated for two
 * different periods produces two distinct rows, and re-generating the same period is an
 * idempotent upsert target. Quests are deterministically regenerable per period (ADR-002 §9),
 * so they are NOT synced — only the claimed XP flows through the ledger.
 *
 * `token_reward` is a reserved-unused extension point (ADR-002 "out of scope": no currency in v1).
 */
@Entity(
    tableName = "quest_instances",
    indices = [
        Index(value = ["period_key"], name = "index_quest_instances_period_key"),
        Index(value = ["status"], name = "index_quest_instances_status"),
    ],
)
data class QuestInstanceEntity(
    /** `{templateId}:{periodKey}`. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** Catalog template id this instance was generated from. */
    @ColumnInfo(name = "template_id")
    val templateId: String,

    /** Period type: "DAILY" / "WEEKLY". */
    @ColumnInfo(name = "period")
    val period: String,

    /** Stable key identifying the concrete period (e.g. "2026-06-11" or "2026-W24"). */
    @ColumnInfo(name = "period_key")
    val periodKey: String,

    /** Target value to complete the quest. */
    @ColumnInfo(name = "target")
    val target: Int,

    /** Current progress toward [target]. */
    @ColumnInfo(name = "progress")
    val progress: Int,

    /** Lifecycle status: "ACTIVE" / "COMPLETED" / "CLAIMED" / "EXPIRED". */
    @ColumnInfo(name = "status")
    val status: String,

    /** Epoch-millis after which the quest expires. */
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long,

    /** XP awarded on claim. */
    @ColumnInfo(name = "xp_reward")
    val xpReward: Int,

    /** Reserved-unused token reward (no currency in v1). Defaults to 0. */
    @ColumnInfo(name = "token_reward", defaultValue = "0")
    val tokenReward: Int = 0,
)
