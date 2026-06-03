package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mmg.manahub.core.data.local.entity.DraftSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for draft simulator sessions.
 *
 * REPLACE conflict strategy is safe on [upsert] because draft_sessions has no
 * child tables with foreign keys pointing at it — there is nothing to cascade-delete.
 * (Contrast with CardEntity, where REPLACE would wipe user_cards.)
 */
@Dao
interface DraftSessionDao {

    /**
     * Emits the single most-recently-updated non-completed session, or null when none.
     * Used to offer "resume draft" on app launch.
     */
    @Query("SELECT * FROM draft_sessions WHERE status != 'COMPLETE' ORDER BY updatedAt DESC LIMIT 1")
    fun observeActiveSession(): Flow<DraftSessionEntity?>

    /** One-shot lookup by id. */
    @Query("SELECT * FROM draft_sessions WHERE id = :id")
    suspend fun getById(id: String): DraftSessionEntity?

    /** Inserts or fully replaces a session row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: DraftSessionEntity)

    /** Marks a session COMPLETE and records the created deck id. */
    @Query("UPDATE draft_sessions SET status = 'COMPLETE', result_deck_id = :deckId, updatedAt = :now WHERE id = :id")
    suspend fun markComplete(id: String, deckId: String, now: Long = System.currentTimeMillis())

    /** Purges all finished sessions (housekeeping). */
    @Query("DELETE FROM draft_sessions WHERE status = 'COMPLETE'")
    suspend fun deleteCompleted()
}
