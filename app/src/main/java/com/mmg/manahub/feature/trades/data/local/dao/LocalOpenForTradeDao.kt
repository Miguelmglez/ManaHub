package com.mmg.manahub.feature.trades.data.local.dao

import androidx.room.*
import com.mmg.manahub.feature.trades.data.local.entity.LocalOpenForTradeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalOpenForTradeDao {

    @Query("SELECT * FROM local_open_for_trade ORDER BY created_at DESC")
    fun observeAll(): Flow<List<LocalOpenForTradeEntity>>

    @Query("SELECT * FROM local_open_for_trade WHERE synced = 0")
    suspend fun getUnsynced(): List<LocalOpenForTradeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: LocalOpenForTradeEntity)

    @Delete
    suspend fun delete(entry: LocalOpenForTradeEntity)

    @Query("DELETE FROM local_open_for_trade WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE local_open_for_trade SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM local_open_for_trade WHERE synced = 1")
    suspend fun clearSynced()

    @Query("SELECT COUNT(*) FROM local_open_for_trade WHERE synced = 0")
    fun observeUnsyncedCount(): Flow<Int>
}
