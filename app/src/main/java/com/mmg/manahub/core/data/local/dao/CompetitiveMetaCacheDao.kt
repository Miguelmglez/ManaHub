package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mmg.manahub.core.data.local.entity.CompetitiveMetaCacheEntity

/**
 * DAO for the [CompetitiveMetaCacheEntity] cache table (Competitive feature, Phase 2).
 *
 * REPLACE-on-conflict is intentional and safe here: this is a pure key-value cache of
 * re-fetchable upstream data with no dependents (no FK, no cascade risk), unlike `CardEntity`,
 * where REPLACE is forbidden because it would cascade-delete `UserCardEntity` rows.
 */
@Dao
interface CompetitiveMetaCacheDao {

    @Query("SELECT * FROM competitive_meta_cache WHERE `format` = :key LIMIT 1")
    suspend fun getByKey(key: String): CompetitiveMetaCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CompetitiveMetaCacheEntity)

    @Query("DELETE FROM competitive_meta_cache")
    suspend fun deleteAll()
}
