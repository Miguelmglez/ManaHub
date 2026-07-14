package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mmg.manahub.core.data.local.entity.CommunityDeckCacheEntity

/**
 * DAO for the [CommunityDeckCacheEntity] cache table.
 *
 * REPLACE-on-conflict is intentional and safe here: this table is a pure cache of
 * re-fetchable upstream data with no dependents, so a DELETE+INSERT cascade has
 * nothing to corrupt (unlike `CardEntity`, where REPLACE is forbidden).
 */
@Dao
interface CommunityDeckCacheDao {

    @Query("SELECT * FROM community_deck_cache WHERE archidekt_id = :id LIMIT 1")
    suspend fun getById(id: Int): CommunityDeckCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CommunityDeckCacheEntity)

    /** Evicts cache rows older than [cutoff] (epoch millis). */
    @Query("DELETE FROM community_deck_cache WHERE cached_at < :cutoff")
    suspend fun evictOlderThan(cutoff: Long)

    @Query("DELETE FROM community_deck_cache")
    suspend fun deleteAll()
}
