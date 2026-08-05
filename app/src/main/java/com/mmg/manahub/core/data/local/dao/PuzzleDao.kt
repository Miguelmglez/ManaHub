package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mmg.manahub.core.data.local.entity.PuzzleResultEntity
import kotlinx.coroutines.flow.Flow

/** DAO for `puzzle_results` (Daily Puzzle feature, Batch B1 foundation). */
@Dao
interface PuzzleDao {

    @Query("SELECT * FROM puzzle_results WHERE puzzle_date = :date LIMIT 1")
    suspend fun getByDate(date: String): PuzzleResultEntity?

    @Query("SELECT * FROM puzzle_results ORDER BY puzzle_date DESC")
    fun observeAll(): Flow<List<PuzzleResultEntity>>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PuzzleResultEntity)
}
