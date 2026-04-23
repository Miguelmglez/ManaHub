package com.mmg.manahub.core.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserCardCollectionDao {

    // ── Write operations ──────────────────────────────────────────────────────

    // Room 2.x @Upsert: inserts if the PK doesn't exist, updates otherwise.
    @Upsert
    fun upsert(entity: UserCardCollectionEntity): Long

    @Upsert
    fun upsertAll(entities: List<UserCardCollectionEntity>)

    // Soft-delete: sets is_deleted = 1 and bumps updated_at so the sync system
    // picks up the tombstone and propagates the deletion to Supabase.
    @Query("UPDATE user_card_collection SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    fun softDelete(id: String, updatedAt: Long = System.currentTimeMillis())

    // Assigns a real userId to all rows created during a guest session (user_id IS NULL or '').
    // Called once on login/registration. Returns count of updated rows so the caller can
    // decide whether to trigger a full sync.
    @Query("UPDATE user_card_collection SET user_id = :newUserId, updated_at = :updatedAt WHERE user_id IS NULL OR user_id = ''")
    fun assignUserId(newUserId: String, updatedAt: Long = System.currentTimeMillis()): Int

    // ── Read operations ───────────────────────────────────────────────────────

    @Query("SELECT * FROM user_card_collection WHERE id = :id AND is_deleted = 0")
    fun getById(id: String): UserCardCollectionEntity?

    // Returns all rows modified after :since — includes is_deleted = 1 rows so tombstones
    // are also pushed during incremental sync.
    @Query("SELECT * FROM user_card_collection WHERE (user_id = :userId OR user_id IS NULL) AND updated_at > :since")
    fun getAllSince(userId: String, since: Long): List<UserCardCollectionEntity>

    // Reactive stream of all non-deleted entries for this user (or guest rows).
    // @Transaction prevents inconsistent reads across the two Room-internal queries for @Relation.
    @Transaction
    @Query("SELECT * FROM user_card_collection WHERE (user_id = :userId OR user_id IS NULL) AND is_deleted = 0 ORDER BY created_at DESC")
    fun observeAll(userId: String?): Flow<List<UserCardWithCard>>

    // Reactive stream for all variants of a single scryfall card.
    @Query("SELECT * FROM user_card_collection WHERE scryfall_id = :scryfallId AND (user_id = :userId OR user_id IS NULL) AND is_deleted = 0")
    fun observeByScryfall(scryfallId: String, userId: String?): Flow<List<UserCardCollectionEntity>>

    // Live count of non-deleted collection entries for UI display.
    @Query("SELECT COUNT(*) FROM user_card_collection WHERE (user_id = :userId OR user_id IS NULL) AND is_deleted = 0")
    fun observeCount(userId: String?): Flow<Int>

    // ── Paging 3 support ──────────────────────────────────────────────────────

    // Returns a PagingSource backed by Room. Requires room-paging dependency.
    // @Transaction ensures @Relation (card join) is consistent across paged loads.
    @Transaction
    @Query("SELECT * FROM user_card_collection WHERE (user_id = :userId OR user_id IS NULL) AND is_deleted = 0 ORDER BY created_at DESC")
    fun getCollectionPagingSource(userId: String?): PagingSource<Int, UserCardWithCard>
}

/** Room relation: one user_card_collection row joined with its card metadata. */
data class UserCardWithCard(
    @Embedded val userCard: UserCardCollectionEntity,
    @Relation(parentColumn = "scryfall_id", entityColumn = "scryfall_id")
    val card: CardEntity?
)
