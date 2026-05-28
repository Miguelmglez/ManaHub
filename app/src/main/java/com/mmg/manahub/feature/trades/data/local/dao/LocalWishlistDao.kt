package com.mmg.manahub.feature.trades.data.local.dao

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
import com.mmg.manahub.feature.trades.data.local.entity.LocalWishlistEntity
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
          AND (is_alt_art = :isAltArt OR (is_alt_art IS NULL AND :isAltArt IS NULL))
        LIMIT 1
    """)
    suspend fun getByAttributes(
        scryfallId: String,
        matchAnyVariant: Boolean,
        isFoil: Boolean?,
        condition: String?,
        language: String?,
        isAltArt: Boolean?
    ): LocalWishlistEntity?

    @Delete
    suspend fun delete(entry: LocalWishlistEntity)

    @Query("DELETE FROM local_wishlists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE local_wishlists SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM local_wishlists WHERE synced = 1")
    suspend fun clearSynced()

    @Query("DELETE FROM local_wishlists WHERE synced = 1 AND id NOT IN (:ids)")
    suspend fun deleteSyncedNotIn(ids: List<String>)

    @Query("SELECT COUNT(*) FROM local_wishlists WHERE synced = 0")
    fun observeUnsyncedCount(): Flow<Int>
}
