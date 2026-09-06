package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Collection sync data-loss fix (linear-moseying-yeti plan), Phase 2, v52 -> v53
 * ([MIGRATION_52_53][com.mmg.manahub.core.data.local.MIGRATION_52_53]): the RESTRICT foreign key
 * to [CardEntity] that used to sit here was REMOVED.
 *
 * An ownership record must never depend on cache metadata being available. The old FK forced the
 * sync PULL loop to skip (and thereby permanently strand, via the watermark) any collection row
 * whose card could not yet be resolved from Scryfall -- see `CardDao`'s upsert KDoc and
 * `SyncManager.ensureCardsExist`, which now writes a `stale_reason = "pending_hydration"`
 * placeholder [CardEntity] for anything Scryfall hasn't returned instead of gating insertion on
 * it. `CardDao.evictStaleCache` never relied on this FK (its cache eviction already excludes
 * referenced ids via `NOT IN` subqueries against this table), so removing it does not change
 * eviction behaviour.
 */
@Entity(
    tableName = "user_card_collection",
    indices = [
        Index("scryfall_id"),
        Index("user_id"),
        Index("updated_at"),
        Index("is_deleted"),
        // Composite unique key mirrors the Supabase unique constraint so that the same
        // physical card variant (foil, condition, language) cannot be inserted twice
        // for the same user.
        Index(
            value = ["user_id", "scryfall_id", "is_foil", "condition", "language"],
            unique = true
        ),
    ]
)
data class UserCardCollectionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,                               // UUID, client-generated
    @ColumnInfo(name = "user_id") val userId: String?,                     // null = guest session
    @ColumnInfo(name = "scryfall_id") val scryfallId: String,
    @ColumnInfo(name = "quantity") val quantity: Int = 1,
    @ColumnInfo(name = "is_foil") val isFoil: Boolean = false,
    @ColumnInfo(name = "condition") val condition: String = "NM",          // M | NM | EX | GD | LP | PL | PO
    @ColumnInfo(name = "language") val language: String = "en",            // ISO: en | ja | de | …
    @ColumnInfo(name = "is_for_trade") val isForTrade: Boolean = false,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,       // soft-delete flag
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
