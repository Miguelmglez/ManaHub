package com.mmg.manahub.feature.draft.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for the draft_sets table.
 * REPLACE conflict strategy is safe here because draft_sets has no child tables
 * with foreign key constraints (unlike CardEntity / UserCardEntity).
 */
@Dao
interface DraftSetDao {

    /** Reactive stream of all sets, ordered by release date descending. */
    @Query("SELECT * FROM draft_sets ORDER BY releasedAt DESC")
    fun getAllSets(): Flow<List<DraftSetEntity>>

    /** Snapshot query for one-shot reads (used in cache checks). */
    @Query("SELECT * FROM draft_sets ORDER BY releasedAt DESC")
    suspend fun getAllSetsSnapshot(): List<DraftSetEntity>

    /** Returns a single set by its lowercase code, or null if not cached. */
    @Query("SELECT * FROM draft_sets WHERE code = :code LIMIT 1")
    suspend fun getSetByCode(code: String): DraftSetEntity?

    /**
     * Returns the most-recent cachedAt timestamp across all rows, used for TTL checks.
     * Using DESC (not ASC) so that a freshly-inserted row makes the cache appear fresh.
     */
    @Query("SELECT cachedAt FROM draft_sets ORDER BY cachedAt DESC LIMIT 1")
    suspend fun getLastCachedTime(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sets: List<DraftSetEntity>)

    @Query("DELETE FROM draft_sets")
    suspend fun deleteAll()

    /**
     * Atomically replaces all sets in the table within a single transaction.
     * Prefer this over calling [deleteAll] + [insertAll] separately to avoid
     * a window where the table is empty between the two operations.
     */
    @Transaction
    suspend fun replaceAll(sets: List<DraftSetEntity>) {
        deleteAll()
        insertAll(sets)
    }
}
