package com.mmg.manahub.core.data.local.dao

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

    // Room 2.x @Upsert: inserts if the PK doesn't exist, updates otherwise.
    @Upsert
    fun upsert(entity: UserCardCollectionEntity): Long

    @Upsert
    fun upsertAll(entities: List<UserCardCollectionEntity>)

    // Soft-delete: sets is_deleted = 1 and bumps updated_at so the sync system
    // picks up the tombstone and propagates the deletion to Supabase.
    @Query("UPDATE user_card_collection SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    fun softDelete(id: String, updatedAt: Long = System.currentTimeMillis())

    // Assigns a real userId to rows created during a guest session (user_id IS NULL or '').
    // Called once on login/registration. Returns count of updated rows so the caller can
    // decide whether to trigger a full sync.
    //
    // Write-path hardening audit (Phase 7, 2026-09-06): the NOT EXISTS guard skips a guest row
    // whose composite tuple (scryfall_id, is_foil, condition, language) already belongs to a LIVE
    // row [newUserId] owns. Migrating it anyway would violate the composite UNIQUE index; the
    // guest row now stays put (user_id remains NULL, still visible -- observeAll/observeAllLocal
    // already include null-user rows) as a PENDING CONFLICT instead of being silently
    // merged-then-deleted. CollectionMergeConflictResolver surfaces these for the user to resolve
    // explicitly (sum / keep account / keep offline) via CollectionMergeConflictSheet -- nothing
    // is ever discarded without that explicit choice.
    @Query("""
        UPDATE user_card_collection SET user_id = :newUserId, updated_at = :updatedAt
        WHERE (user_id IS NULL OR user_id = '')
          AND NOT EXISTS (
              SELECT 1 FROM user_card_collection existing
              WHERE existing.user_id = :newUserId
                AND existing.is_deleted = 0
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

    // Used by the sync PULL loop for LWW comparison. Unlike getById, this includes
    // soft-deleted rows (tombstones) so a local deletion is never overwritten by an
    // older remote row that hasn't been deleted yet.
    @Query("SELECT * FROM user_card_collection WHERE id = :id")
    fun getByIdIncludingDeleted(id: String): UserCardCollectionEntity?

    // UUID reconciliation: finds a local row that matches the Supabase composite key but
    // may have a different UUID (guest-generated vs. Supabase-canonical). Used in the
    // PULL loop to detect and fix UUID mismatches before upserting the remote row.
    @Query("SELECT * FROM user_card_collection WHERE user_id = :userId AND scryfall_id = :scryfallId AND is_foil = :isFoil AND condition = :condition AND language = :language LIMIT 1")
    fun getByCompositeKey(userId: String, scryfallId: String, isFoil: Boolean, condition: String, language: String): UserCardCollectionEntity?

    // Guest-session variant: matches rows where user_id is NULL or empty.
    @Query("SELECT * FROM user_card_collection WHERE (user_id IS NULL OR user_id = '') AND scryfall_id = :scryfallId AND is_foil = :isFoil AND condition = :condition AND language = :language LIMIT 1")
    fun getByCompositeKeyGuest(scryfallId: String, isFoil: Boolean, condition: String, language: String): UserCardCollectionEntity?

    // Hard-delete used only for UUID reconciliation: removes the stale guest-UUID row so
    // the Supabase-canonical UUID row can be inserted without a composite UNIQUE conflict.
    @Query("DELETE FROM user_card_collection WHERE id = :id")
    fun deleteById(id: String)

    // Atomic UUID reconciliation: deletes the stale local row and inserts the
    // Supabase-canonical row in a single transaction so a process-kill between the
    // two operations can never leave the collection in a partially-updated state.
    @Transaction
    fun reconcileAndUpsert(deleteId: String, entity: UserCardCollectionEntity) {
        deleteById(deleteId)
        upsert(entity)
    }

    // Returns all rows modified after :since — includes is_deleted = 1 rows so tombstones
    // are also pushed during incremental sync.
    @Query("SELECT * FROM user_card_collection WHERE (user_id = :userId OR user_id IS NULL) AND updated_at > :since")
    fun getAllSince(userId: String, since: Long): List<UserCardCollectionEntity>

    // Returns all rows without a userId (guest/offline rows). Used by SyncManager to
    // resolve UNIQUE-constraint conflicts before calling assignUserId on login.
    @Query("SELECT * FROM user_card_collection WHERE user_id IS NULL OR user_id = ''")
    fun getAllGuestRows(): List<UserCardCollectionEntity>

    // Reactive stream of all non-deleted entries for this user (or guest rows).
    // @Transaction prevents inconsistent reads across the two Room-internal queries for @Relation.
    @Transaction
    @Query("SELECT * FROM user_card_collection WHERE (user_id = :userId OR user_id IS NULL) AND is_deleted = 0 ORDER BY created_at DESC")
    fun observeAll(userId: String?): Flow<List<UserCardWithCard>>

    // Reactive stream of ALL non-deleted entries regardless of userId.
    // Used when the user is logged out so that locally-stored cards remain visible.
    @Transaction
    @Query("SELECT * FROM user_card_collection WHERE is_deleted = 0 ORDER BY created_at DESC")
    fun observeAllLocal(): Flow<List<UserCardWithCard>>

    // Newest-first, capped stream for the Home dashboard's Recently Added widget.
    // Reuses the existing created_at index — no schema change (UserCardCollectionEntity
    // already carries createdAt/updatedAt).
    @Transaction
    @Query("SELECT * FROM user_card_collection WHERE (user_id = :userId OR user_id IS NULL) AND is_deleted = 0 ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(userId: String?, limit: Int): Flow<List<UserCardWithCard>>

    // Guest/logged-out variant: all local rows regardless of userId, capped.
    @Transaction
    @Query("SELECT * FROM user_card_collection WHERE is_deleted = 0 ORDER BY created_at DESC LIMIT :limit")
    fun observeRecentLocal(limit: Int): Flow<List<UserCardWithCard>>

    // Reactive stream for all variants of a single scryfall card.
    @Query("SELECT * FROM user_card_collection WHERE scryfall_id = :scryfallId AND (user_id = :userId OR user_id IS NULL) AND is_deleted = 0")
    fun observeByScryfall(scryfallId: String, userId: String?): Flow<List<UserCardCollectionEntity>>

    // Live count of non-deleted collection entries for UI display.
    @Query("SELECT COUNT(*) FROM user_card_collection WHERE (user_id = :userId OR user_id IS NULL) AND is_deleted = 0")
    fun observeCount(userId: String?): Flow<Int>

    @Query("SELECT DISTINCT scryfall_id FROM user_card_collection WHERE is_deleted = 0")
    fun getAllScryfallIds(): List<String>

    // Returns the number of non-deleted collection rows owned by [userId].
    // Used by SyncManager.assignUserIdAndSync to detect a wiped Room DB (count == 0
    // after assignUserId ran but no guest rows were migrated), which means the DataStore
    // watermark must be cleared to force a full pull from Supabase.
    @Query("SELECT COUNT(*) FROM user_card_collection WHERE user_id = :userId AND is_deleted = 0")
    fun getCountForUser(userId: String): Int

    // Counts rows (including tombstones) modified after [since] for the exact [userId].
    // Used by SyncManager.countPendingChanges to drive the "Sync your collection" banner.
    // Unlike getAllSince this does NOT include NULL-userId guest rows — those must be
    // migrated via assignUserIdAndSync first and must not inflate the banner count.
    @Query("SELECT COUNT(*) FROM user_card_collection WHERE user_id = :userId AND updated_at > :since")
    fun countPendingSync(userId: String, since: Long): Int

    // Collection sync data-loss fix, Phase 6: total row count for [userId] INCLUDING tombstones
    // -- mirrors the server's get_collection_integrity().total_rows exactly (that RPC also
    // counts tombstones). Used by SyncManager's post-sync integrity self-check to detect a
    // client/server row-count disagreement.
    @Query("SELECT COUNT(*) FROM user_card_collection WHERE user_id = :userId")
    fun getTotalRowCountForUser(userId: String): Int

    // Live (non-deleted) summed quantity for [userId] -- mirrors get_collection_integrity()
    // .liveQuantity. Diagnostic-only (logged alongside a mismatch), not itself the repair trigger.
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM user_card_collection WHERE user_id = :userId AND is_deleted = 0")
    fun getLiveQuantityForUser(userId: String): Int

    // Card Versions & Languages, Phase 1A. Every non-deleted collection row for ANY
    // printing/language sharing the same oracle identity as the card being viewed — feeds
    // CardDetail's "your other copies" section (Phase 1B). The subquery matches by oracle_id
    // when it is known; rows whose cached CardEntity predates the oracle_id column (oracle_id =
    // '') are related by an exact match on the English oracle `name` instead. When [oracleId]
    // itself is blank (the viewed card's own cache row predates oracle_id), the first branch is
    // never true, so matching correctly falls through to name-only — it does NOT widen to every
    // other oracle_id='' card in the table.
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
