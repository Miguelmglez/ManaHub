package com.mmg.magicfolder.feature.draft.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftSetDao {

    @Query("SELECT * FROM draft_sets ORDER BY releasedAt DESC")
    fun getAllSets(): Flow<List<DraftSetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sets: List<DraftSetEntity>)

    @Query("DELETE FROM draft_sets")
    suspend fun deleteAll()

    @Query("SELECT * FROM draft_sets ORDER BY releasedAt DESC")
    suspend fun getAllSetsSnapshot(): List<DraftSetEntity>

    @Query("SELECT cachedAt FROM draft_sets LIMIT 1")
    suspend fun getLastCachedTime(): Long?
}
