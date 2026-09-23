package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.data.local.entity.LocalWishlistEntity
import kotlinx.coroutines.flow.Flow

data class LocalWishlistWithCard(
    @Embedded val entity: LocalWishlistEntity,
    @Relation(
        parentColumn = "scryfall_id",
        entityColumn = "scryfall_id"
    )
    val card: CardEntity?
)

@Dao
interface LocalWishlistDao {

    @Transaction
    @Query("SELECT * FROM local_wishlists ORDER BY created_at DESC")
    fun observeAllWithCard(): Flow<List<LocalWishlistWithCard>>

    @Query("SELECT * FROM local_wishlists ORDER BY created_at DESC")
    fun observeAll(): Flow<List<LocalWishlistEntity>>

    @Transaction
    @Query("SELECT * FROM local_wishlists WHERE scryfall_id = :scryfallId ORDER BY created_at DESC")
    fun observeByScryfallIdWithCard(scryfallId: String): Flow<List<LocalWishlistWithCard>>

    @Query("SELECT * FROM local_wishlists WHERE scryfall_id = :scryfallId ORDER BY created_at DESC")
    fun observeByScryfallId(scryfallId: String): Flow<List<LocalWishlistEntity>>

    @Query("SELECT * FROM local_wishlists WHERE scryfall_id = :scryfallId")
    suspend fun getByScryfallId(scryfallId: String): List<LocalWishlistEntity>

    // Card Versions & Languages, Phase 1A. Every wishlist entry for ANY printing/language sharing
    // the same oracle identity — see UserCardCollectionDao.observeVersionsByOracle for the exact
    // matching semantics (oracle_id when known, exact `name` match as the pre-oracle_id fallback).
    @Transaction
    @Query("""
        SELECT * FROM local_wishlists
        WHERE scryfall_id IN (
            SELECT scryfall_id FROM cards
            WHERE (:oracleId != '' AND oracle_id = :oracleId)
               OR (oracle_id = '' AND name = :name)
        )
        ORDER BY created_at DESC
    """)
    fun observeVersionsByOracle(oracleId: String, name: String): Flow<List<LocalWishlistWithCard>>

    // Used to check `synced` before mutating a row, so local edits to an already-synced entry
    // can be paired with the matching remote call (trades audit §2.3, 2026-07-10).
    @Query("SELECT * FROM local_wishlists WHERE id = :id")
    suspend fun getById(id: String): LocalWishlistEntity?

    @Query("SELECT * FROM local_wishlists WHERE synced = 0")
    suspend fun getUnsynced(): List<LocalWishlistEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: LocalWishlistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<LocalWishlistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<LocalWishlistEntity>)

    @Update
    suspend fun update(entry: LocalWishlistEntity)

    @Query("UPDATE local_wishlists SET quantity = :quantity WHERE id = :id")
    suspend fun updateQuantity(id: String, quantity: Int)

    @Query("""
        SELECT * FROM local_wishlists
        WHERE scryfall_id = :scryfallId
          AND match_any_variant = :matchAnyVariant
          AND (is_foil = :isFoil OR (is_foil IS NULL AND :isFoil IS NULL))
          AND (condition = :condition OR (condition IS NULL AND :condition IS NULL))
          AND (language = :language OR (language IS NULL AND :language IS NULL))
        LIMIT 1
    """)
    suspend fun getByAttributes(
        scryfallId: String,
        matchAnyVariant: Boolean,
        isFoil: Boolean?,
        condition: String?,
        language: String?,
    ): LocalWishlistEntity?

    /** Inserts or merges every entry in one transaction; merged rows go back to unsynced. */
    @Transaction
    suspend fun addOrMergeAll(entries: List<LocalWishlistEntity>) {
        entries.forEach { entry ->
            val existing = getByAttributes(
                scryfallId = entry.scryfallId,
                matchAnyVariant = entry.matchAnyVariant,
                isFoil = entry.isFoil,
                condition = entry.condition,
                language = entry.language,
            )
            if (existing != null) {
                // Summed as Long first, like the collection path: an Int overflow here would
                // write a negative wanted quantity.
                val merged = (existing.quantity.toLong() + entry.quantity)
                    .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                update(existing.copy(quantity = merged, synced = false))
            } else {
                insert(entry)
            }
        }
    }

    @Delete
    suspend fun delete(entry: LocalWishlistEntity)

    @Query("DELETE FROM local_wishlists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE local_wishlists SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM local_wishlists WHERE synced = 1")
    suspend fun clearSynced()

    /**
     * Removes rows that belong to an account other than [userId]: rows owned by someone else, and
     * server-backed rows not proven to be [userId]'s (they re-download on the next sync). Guest
     * rows (null owner, unsynced) survive and migrate to [userId].
     */
    @Query("""
        DELETE FROM local_wishlists
        WHERE (owner_user_id IS NOT NULL AND owner_user_id != :userId)
           OR (synced = 1 AND (owner_user_id IS NULL OR owner_user_id != :userId))
    """)
    suspend fun deleteForeignAccountRows(userId: String)

    /** Claims ownerless rows [ids] for [ownerUserId] (guest rows just migrated to that account). */
    @Query("UPDATE local_wishlists SET owner_user_id = :ownerUserId WHERE id IN (:ids) AND owner_user_id IS NULL")
    suspend fun stampOwner(ids: List<String>, ownerUserId: String)

    @Query("DELETE FROM local_wishlists WHERE synced = 1 AND id NOT IN (:ids)")
    suspend fun deleteSyncedNotIn(ids: List<String>)

    @Query("SELECT COUNT(*) FROM local_wishlists WHERE synced = 0")
    fun observeUnsyncedCount(): Flow<Int>
}
