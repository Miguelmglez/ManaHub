package com.mmg.manahub.feature.trades.data.local.dao

import androidx.room.*
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.feature.trades.data.local.entity.LocalOpenForTradeEntity
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

    @Query("SELECT * FROM local_open_for_trade WHERE local_collection_id = :collectionId LIMIT 1")
    suspend fun getByCollectionId(collectionId: String): LocalOpenForTradeEntity?

    @Query("SELECT * FROM local_open_for_trade WHERE synced = 0")
    suspend fun getUnsynced(): List<LocalOpenForTradeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: LocalOpenForTradeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: LocalOpenForTradeEntity)

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

    @Query("SELECT COUNT(*) FROM local_open_for_trade WHERE synced = 0")
    fun observeUnsyncedCount(): Flow<Int>
}
