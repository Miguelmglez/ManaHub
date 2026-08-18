package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mmg.manahub.core.data.local.entity.CompetitiveLimitedRatingsCacheEntity

/**
 * DAO for the [CompetitiveLimitedRatingsCacheEntity] cache table (Competitive feature, Phase 2).
 *
 * REPLACE-on-conflict is intentional and safe here: this is a pure key-value cache of
 * re-fetchable upstream data with no dependents (no FK, no cascade risk), unlike `CardEntity`,
 * where REPLACE is forbidden because it would cascade-delete `UserCardEntity` rows.
 */
@Dao
interface CompetitiveLimitedRatingsCacheDao {

    @Query("SELECT * FROM competitive_limited_ratings_cache WHERE `set_code` = :key LIMIT 1")
    suspend fun getByKey(key: String): CompetitiveLimitedRatingsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CompetitiveLimitedRatingsCacheEntity)

    @Query("DELETE FROM competitive_limited_ratings_cache")
    suspend fun deleteAll()
}
