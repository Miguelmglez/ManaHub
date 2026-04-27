package com.mmg.manahub.feature.trades.data.local.dao

import androidx.room.*
import com.mmg.manahub.feature.trades.data.local.entity.LocalWishlistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalWishlistDao {

    @Query("SELECT * FROM local_wishlists ORDER BY created_at DESC")
    fun observeAll(): Flow<List<LocalWishlistEntity>>

    @Query("SELECT * FROM local_wishlists WHERE synced = 0")
    suspend fun getUnsynced(): List<LocalWishlistEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: LocalWishlistEntity)

    @Delete
    suspend fun delete(entry: LocalWishlistEntity)

    @Query("DELETE FROM local_wishlists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE local_wishlists SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM local_wishlists WHERE synced = 1")
    suspend fun clearSynced()

    @Query("SELECT COUNT(*) FROM local_wishlists WHERE synced = 0")
    fun observeUnsyncedCount(): Flow<Int>
}
