package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mmg.manahub.core.data.local.entity.CommunityAggregateEntity

/**
 * DAO for the [CommunityAggregateEntity] cache table.
 *
 * REPLACE-on-conflict is intentional and safe here: this is a pure key-value cache of
 * re-fetchable upstream data with no dependents (no FK, no cascade risk), unlike `CardEntity`,
 * where REPLACE is forbidden because it would cascade-delete `UserCardEntity` rows.
 */
@Dao
interface CommunityAggregateDao {

    @Query("SELECT * FROM community_aggregate_cache WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): CommunityAggregateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CommunityAggregateEntity)
}
