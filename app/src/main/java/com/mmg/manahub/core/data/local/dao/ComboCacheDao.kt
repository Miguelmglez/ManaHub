package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mmg.manahub.core.data.local.entity.ComboCacheEntity

/**
 * DAO for the [ComboCacheEntity] cache table (Deck Engine Unification plan D7, Phase 4.3).
 *
 * REPLACE-on-conflict is intentional and safe: a pure key-value cache of re-fetchable upstream
 * data with no dependents (no FK, no cascade risk) -- same rationale as [CommunityAggregateDao].
 */
@Dao
interface ComboCacheDao {

    @Query("SELECT * FROM combo_cache WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): ComboCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ComboCacheEntity)
}
