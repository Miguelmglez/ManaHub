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
    @Query("SELECT * FROM local_open_for_trade WHERE owner_user_id = :ownerUserId ORDER BY created_at DESC")
    fun observeAllWithCard(ownerUserId: String): Flow<List<LocalOpenForTradeWithCard>>

    @Query("SELECT * FROM local_open_for_trade WHERE owner_user_id = :ownerUserId ORDER BY created_at DESC")
    fun observeAll(ownerUserId: String): Flow<List<LocalOpenForTradeEntity>>

    @Query("SELECT * FROM local_open_for_trade WHERE scryfall_id = :scryfallId AND owner_user_id = :ownerUserId ORDER BY created_at DESC")
    fun observeByScryfallId(scryfallId: String, ownerUserId: String): Flow<List<LocalOpenForTradeEntity>>

    // Card Versions & Languages, Phase 1A. Every open-for-trade entry for ANY printing/language
    // sharing the same oracle identity — see UserCardCollectionDao.observeVersionsByOracle for
    // the exact matching semantics (oracle_id when known, exact `name` match as fallback).
    @Transaction
    @Query("""
        SELECT * FROM local_open_for_trade
        WHERE owner_user_id = :ownerUserId AND scryfall_id IN (
            SELECT scryfall_id FROM cards
            WHERE (:oracleId != '' AND oracle_id = :oracleId)
               OR (oracle_id = '' AND name = :name)
        )
        ORDER BY created_at DESC
    """)
    fun observeVersionsByOracle(oracleId: String, name: String, ownerUserId: String): Flow<List<LocalOpenForTradeWithCard>>

    @Query("SELECT * FROM local_open_for_trade WHERE local_collection_id = :collectionId AND owner_user_id = :ownerUserId LIMIT 1")
    suspend fun getByCollectionId(collectionId: String, ownerUserId: String): LocalOpenForTradeEntity?

    @Query("""
        SELECT * FROM local_open_for_trade
        WHERE scryfall_id = :scryfallId AND owner_user_id = :ownerUserId AND is_foil = :isFoil
          AND condition = :condition AND language = :language
        ORDER BY created_at ASC
        LIMIT 1
    """)
    suspend fun getByAttributes(
        scryfallId: String,
        isFoil: Boolean,
        condition: String,
        language: String,
        ownerUserId: String,
    ): LocalOpenForTradeEntity?

    @Query("SELECT * FROM local_open_for_trade WHERE synced = 0 AND owner_user_id IN (:ownerUserId, 'local_guest')")
    suspend fun getUnsynced(ownerUserId: String): List<LocalOpenForTradeEntity>

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

    @Query("DELETE FROM local_open_for_trade WHERE id = :id AND owner_user_id = :ownerUserId")
    suspend fun deleteById(id: String, ownerUserId: String)

    @Query("DELETE FROM local_open_for_trade WHERE local_collection_id = :collectionId AND owner_user_id = :ownerUserId")
    suspend fun deleteByCollectionId(collectionId: String, ownerUserId: String)

    @Query("UPDATE local_open_for_trade SET synced = 1 WHERE id IN (:ids) AND owner_user_id IN (:ownerUserId, 'local_guest')")
    suspend fun markSynced(ids: List<String>, ownerUserId: String)

    @Query("DELETE FROM local_open_for_trade WHERE synced = 1 AND owner_user_id = :ownerUserId")
    suspend fun clearSynced(ownerUserId: String)

    /** Removes ambiguous legacy rows while retaining verified rows for every account. */
    @Query("""
        DELETE FROM local_open_for_trade
        WHERE owner_user_id IS NULL
           OR (synced = 1 AND owner_user_id = 'local_guest')
    """)
    suspend fun deleteAmbiguousRows()

    /** Claims verified guest rows [ids] for [ownerUserId] after migration. */
    @Query("UPDATE local_open_for_trade SET owner_user_id = :ownerUserId WHERE id IN (:ids) AND owner_user_id = 'local_guest'")
    suspend fun stampOwner(ids: List<String>, ownerUserId: String)

    @Query("SELECT id FROM local_open_for_trade WHERE synced = 1 AND owner_user_id = :ownerUserId")
    suspend fun getSyncedIds(ownerUserId: String): List<String>

    // Callers chunk [ids]: API 29's SQLite caps a statement at 999 bind variables.
    @Query("DELETE FROM local_open_for_trade WHERE synced = 1 AND owner_user_id = :ownerUserId AND id IN (:ids)")
    suspend fun deleteSyncedByIds(ids: List<String>, ownerUserId: String)

    @Query("DELETE FROM local_open_for_trade WHERE synced = 1 AND owner_user_id = :ownerUserId AND id NOT IN (:ids)")
    suspend fun deleteSyncedNotIn(ids: List<String>, ownerUserId: String)

    @Query("SELECT COUNT(*) FROM local_open_for_trade WHERE synced = 0 AND owner_user_id = :ownerUserId")
    fun observeUnsyncedCount(ownerUserId: String): Flow<Int>
}
