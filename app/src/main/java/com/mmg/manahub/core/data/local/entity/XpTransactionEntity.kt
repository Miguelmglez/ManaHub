package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per XP grant — the append-only XP ledger (ADR-002 §3).
 *
 * The UNIQUE index on [idempotencyKey] is the crash-safety + (Phase 4) conflict-free-sync
 * foundation: re-processing the same event is rejected at INSERT time, so XP is a monotonic
 * set-union of ledger rows, never last-write-wins. Inserts use `OnConflictStrategy.IGNORE` and
 * return the rowId; a duplicate returns -1 → the granter treats it as a no-op.
 *
 * `source_category` stores the [com.mmg.manahub.core.gamification.domain.model.XpSourceCategory]
 * enum NAME (a stable string). `source_ref` is an optional free-form reference to the originating
 * entity (e.g. a deckId or friendId) used by the granter's per-category cap queries.
 */
@Entity(
    tableName = "xp_transactions",
    indices = [
        Index(value = ["idempotency_key"], unique = true, name = "index_xp_transactions_idempotency_key"),
        Index(value = ["source_category"], name = "index_xp_transactions_source_category"),
    ],
)
data class XpTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Deterministic/unique key from the event; UNIQUE — duplicate inserts are rejected. */
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,

    /** XP granted by this ledger row (always >= 0). */
    @ColumnInfo(name = "amount")
    val amount: Int,

    /** [com.mmg.manahub.core.gamification.domain.model.XpSourceCategory] name. */
    @ColumnInfo(name = "source_category")
    val sourceCategory: String,

    /** Optional reference to the source entity (deckId, friendId, sessionId, …) for cap queries. */
    @ColumnInfo(name = "source_ref")
    val sourceRef: String?,

    /** Epoch-millis when this grant was recorded. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
