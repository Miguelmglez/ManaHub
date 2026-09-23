package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.data.local.entity.LocalOpenForTradeEntity
import kotlinx.coroutines.flow.Flow

data class LocalOpenForTradeWithCard(
    @Embedded val entity: LocalOpenForTradeEntity,
    @Relation(
        parentColumn = "scryfall_id",
        entityColumn = "scryfall_id"
    )
    val card: CardEntity?
)

@Dao
interface LocalOpenForTradeDao {

    @Transaction
    @Query("SELECT * FROM local_open_for_trade ORDER BY created_at DESC")
    fun observeAllWithCard(): Flow<List<LocalOpenForTradeWithCard>>

    @Query("SELECT * FROM local_open_for_trade ORDER BY created_at DESC")
    fun observeAll(): Flow<List<LocalOpenForTradeEntity>>

    @Query("SELECT * FROM local_open_for_trade WHERE scryfall_id = :scryfallId ORDER BY created_at DESC")
    fun observeByScryfallId(scryfallId: String): Flow<List<LocalOpenForTradeEntity>>

    // Card Versions & Languages, Phase 1A. Every open-for-trade entry for ANY printing/language
    // sharing the same oracle identity — see UserCardCollectionDao.observeVersionsByOracle for
    // the exact matching semantics (oracle_id when known, exact `name` match as fallback).
    @Transaction
    @Query("""
        SELECT * FROM local_open_for_trade
        WHERE scryfall_id IN (
            SELECT scryfall_id FROM cards
            WHERE (:oracleId != '' AND oracle_id = :oracleId)
               OR (oracle_id = '' AND name = :name)
        )
        ORDER BY created_at DESC
    """)
    fun observeVersionsByOracle(oracleId: String, name: String): Flow<List<LocalOpenForTradeWithCard>>

    @Query("SELECT * FROM local_open_for_trade WHERE local_collection_id = :collectionId LIMIT 1")
    suspend fun getByCollectionId(collectionId: String): LocalOpenForTradeEntity?

    @Query("""
        SELECT * FROM local_open_for_trade
        WHERE scryfall_id = :scryfallId AND is_foil = :isFoil
          AND condition = :condition AND language = :language
        ORDER BY created_at ASC
        LIMIT 1
    """)
    suspend fun getByAttributes(
        scryfallId: String,
        isFoil: Boolean,
        condition: String,
        language: String,
    ): LocalOpenForTradeEntity?

    @Query("SELECT * FROM local_open_for_trade WHERE synced = 0")
    suspend fun getUnsynced(): List<LocalOpenForTradeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: LocalOpenForTradeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: LocalOpenForTradeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<LocalOpenForTradeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<LocalOpenForTradeEntity>)

    @Delete
    suspend fun delete(entry: LocalOpenForTradeEntity)

    @Query("DELETE FROM local_open_for_trade WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM local_open_for_trade WHERE local_collection_id = :collectionId")
    suspend fun deleteByCollectionId(collectionId: String)

    @Query("UPDATE local_open_for_trade SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM local_open_for_trade WHERE synced = 1")
    suspend fun clearSynced()

    /**
     * Removes rows that belong to an account other than [userId]: rows owned by someone else, and
     * server-backed rows not proven to be [userId]'s (they re-download on the next sync). Guest
     * rows (null owner, unsynced) survive and migrate to [userId].
     */
    @Query("""
        DELETE FROM local_open_for_trade
        WHERE (owner_user_id IS NOT NULL AND owner_user_id != :userId)
           OR (synced = 1 AND (owner_user_id IS NULL OR owner_user_id != :userId))
    """)
    suspend fun deleteForeignAccountRows(userId: String)

    /** Claims ownerless rows [ids] for [ownerUserId] (guest rows just migrated to that account). */
    @Query("UPDATE local_open_for_trade SET owner_user_id = :ownerUserId WHERE id IN (:ids) AND owner_user_id IS NULL")
    suspend fun stampOwner(ids: List<String>, ownerUserId: String)

    @Query("DELETE FROM local_open_for_trade WHERE synced = 1 AND id NOT IN (:ids)")
    suspend fun deleteSyncedNotIn(ids: List<String>)

    @Query("SELECT COUNT(*) FROM local_open_for_trade WHERE synced = 0")
    fun observeUnsyncedCount(): Flow<Int>
}
