package com.mmg.manahub.core.data.local.dao
// COMMENTS_REVIEWED: 2026-09-06

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserCardCollectionDao {

    // ── Write operations ──────────────────────────────────────────────────────

    @Upsert
    fun upsert(entity: UserCardCollectionEntity): Long

    @Upsert
    fun upsertAll(entities: List<UserCardCollectionEntity>)

    @Query("UPDATE user_card_collection SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    fun softDelete(id: String, updatedAt: Long = System.currentTimeMillis())

    // Write-path hardening audit (2026-09-06): the guard now matches the composite UNIQUE index
    // EXACTLY (no is_deleted filter) -- Room's @Index cannot express a partial index, so a
    // tombstoned row still occupies its tuple and would throw SQLiteConstraintException on this
    // UPDATE if the guard let it through. A colliding guest row (live OR tombstoned collider)
    // simply stays parked at user_id NULL as a pending conflict; CollectionMergeConflictResolver
    // surfaces it for the user to resolve explicitly instead.
    @Query("""
        UPDATE user_card_collection SET user_id = :newUserId, updated_at = :updatedAt
        WHERE (user_id IS NULL OR user_id = '')
          AND NOT EXISTS (
              SELECT 1 FROM user_card_collection existing
              WHERE existing.user_id = :newUserId
                AND existing.scryfall_id = user_card_collection.scryfall_id
                AND existing.is_foil = user_card_collection.is_foil
                AND existing.condition = user_card_collection.condition
                AND existing.language = user_card_collection.language
          )
    """)
    fun assignUserId(newUserId: String, updatedAt: Long = System.currentTimeMillis()): Int

    // ── Read operations ───────────────────────────────────────────────────────

    @Query("SELECT * FROM user_card_collection WHERE id = :id AND is_deleted = 0")
    fun getById(id: String): UserCardCollectionEntity?

    // Includes tombstones so an older remote row can never resurrect a local deletion.
    @Query("SELECT * FROM user_card_collection WHERE id = :id")
    fun getByIdIncludingDeleted(id: String): UserCardCollectionEntity?

    // Includes tombstones -- must match assignUserId's guard predicate exactly so a
    // tombstone-collision conflict is surfaced the same way it was parked.
    @Query("SELECT * FROM user_card_collection WHERE user_id = :userId AND scryfall_id = :scryfallId AND is_foil = :isFoil AND condition = :condition AND language = :language LIMIT 1")
    fun getByCompositeKey(userId: String, scryfallId: String, isFoil: Boolean, condition: String, language: String): UserCardCollectionEntity?

    @Query("SELECT * FROM user_card_collection WHERE (user_id IS NULL OR user_id = '') AND scryfall_id = :scryfallId AND is_foil = :isFoil AND condition = :condition AND language = :language LIMIT 1")
    fun getByCompositeKeyGuest(scryfallId: String, isFoil: Boolean, condition: String, language: String): UserCardCollectionEntity?

    // Hard-delete: only safe where PUSH already flushed this row's state this cycle (UUID
    // reconciliation) or the row is being folded into another via resolveMergeConflict.
    @Query("DELETE FROM user_card_collection WHERE id = :id")
    fun deleteById(id: String)

    @Transaction
    fun reconcileAndUpsert(deleteId: String, entity: UserCardCollectionEntity) {
        deleteById(deleteId)
        upsert(entity)
    }

    // Write-path hardening audit (2026-09-06): atomic counterpart to
    // CollectionMergeConflictResolver.resolve -- a process-kill between the account-row write and
    // the guest-row delete would otherwise double-count on the next resolve attempt.
    // [resolvedAccountRow] is null for KEEP_ACCOUNT (nothing to write, guest row just drops).
    @Transaction
    fun resolveMergeConflict(resolvedAccountRow: UserCardCollectionEntity?, guestRowId: String) {
        resolvedAccountRow?.let { upsert(it) }
        deleteById(guestRowId)
    }

    @Query("SELECT * FROM user_card_collection WHERE (user_id = :userId OR user_id IS NULL) AND updated_at > :since")
    fun getAllSince(userId: String, since: Long): List<UserCardCollectionEntity>

    @Query("SELECT * FROM user_card_collection WHERE user_id IS NULL OR user_id = ''")
    fun getAllGuestRows(): List<UserCardCollectionEntity>

    @Transaction
    @Query("SELECT * FROM user_card_collection WHERE (user_id = :userId OR user_id IS NULL) AND is_deleted = 0 ORDER BY created_at DESC")
    fun observeAll(userId: String?): Flow<List<UserCardWithCard>>

    // Used when logged out so locally-stored cards remain visible.
    @Transaction
    @Query("SELECT * FROM user_card_collection WHERE is_deleted = 0 ORDER BY created_at DESC")
    fun observeAllLocal(): Flow<List<UserCardWithCard>>

    @Transaction
    @Query("SELECT * FROM user_card_collection WHERE (user_id = :userId OR user_id IS NULL) AND is_deleted = 0 ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(userId: String?, limit: Int): Flow<List<UserCardWithCard>>

    @Transaction
    @Query("SELECT * FROM user_card_collection WHERE is_deleted = 0 ORDER BY created_at DESC LIMIT :limit")
    fun observeRecentLocal(limit: Int): Flow<List<UserCardWithCard>>

    @Query("SELECT * FROM user_card_collection WHERE scryfall_id = :scryfallId AND (user_id = :userId OR user_id IS NULL) AND is_deleted = 0")
    fun observeByScryfall(scryfallId: String, userId: String?): Flow<List<UserCardCollectionEntity>>

    @Query("SELECT COUNT(*) FROM user_card_collection WHERE (user_id = :userId OR user_id IS NULL) AND is_deleted = 0")
    fun observeCount(userId: String?): Flow<Int>

    @Query("SELECT DISTINCT scryfall_id FROM user_card_collection WHERE is_deleted = 0")
    fun getAllScryfallIds(): List<String>

    // Used by SyncManager.assignUserIdAndSync to detect a wiped Room DB and force a full re-pull.
    @Query("SELECT COUNT(*) FROM user_card_collection WHERE user_id = :userId AND is_deleted = 0")
    fun getCountForUser(userId: String): Int

    // Excludes NULL-userId guest rows -- those must migrate via assignUserId first, or the "Sync
    // your collection" banner count would be inflated by rows that aren't safe to push yet.
    @Query("SELECT COUNT(*) FROM user_card_collection WHERE user_id = :userId AND updated_at > :since")
    fun countPendingSync(userId: String, since: Long): Int

    // Includes tombstones to mirror get_collection_integrity().total_rows exactly.
    @Query("SELECT COUNT(*) FROM user_card_collection WHERE user_id = :userId")
    fun getTotalRowCountForUser(userId: String): Int

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM user_card_collection WHERE user_id = :userId AND is_deleted = 0")
    fun getLiveQuantityForUser(userId: String): Int

    // Matches by oracle_id when known; falls back to exact English name for cache rows that
    // predate the oracle_id backfill (never widens to every oracle_id='' row when oracleId is blank).
    @Transaction
    @Query("""
        SELECT * FROM user_card_collection
        WHERE is_deleted = 0
          AND (user_id = :userId OR user_id IS NULL)
          AND scryfall_id IN (
              SELECT scryfall_id FROM cards
              WHERE (:oracleId != '' AND oracle_id = :oracleId)
                 OR (oracle_id = '' AND name = :name)
          )
        ORDER BY created_at DESC
    """)
    fun observeVersionsByOracle(oracleId: String, name: String, userId: String?): Flow<List<UserCardWithCard>>

    // ── Paging 3 support ──────────────────────────────────────────────────────

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
