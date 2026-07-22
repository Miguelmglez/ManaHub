package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mmg.manahub.core.data.local.entity.CardStrategyTagsCacheEntity

/**
 * DAO for the [CardStrategyTagsCacheEntity] cache table (Deck Engine Unification plan, D8, §5
 * Phase 5c).
 *
 * REPLACE-on-conflict is intentional and safe: a pure key-value cache of re-fetchable upstream
 * data with no dependents (no FK, no cascade risk) -- same rationale as [ComboCacheDao].
 */
@Dao
interface CardStrategyTagsCacheDao {

    @Query("SELECT * FROM card_strategy_tags_cache WHERE oracle_id = :oracleId LIMIT 1")
    suspend fun getByOracleId(oracleId: String): CardStrategyTagsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CardStrategyTagsCacheEntity)
}
