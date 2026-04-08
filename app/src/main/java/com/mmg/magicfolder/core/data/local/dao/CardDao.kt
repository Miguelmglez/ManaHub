package com.mmg.magicfolder.core.data.local.dao


import androidx.room.*
import com.mmg.magicfolder.core.data.local.entity.CardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: CardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(cards: List<CardEntity>)

    @Query("SELECT * FROM cards WHERE scryfall_id = :id")
    suspend fun getById(id: String): CardEntity?

    @Query("SELECT * FROM cards WHERE scryfall_id = :id")
    fun observeById(id: String): Flow<CardEntity?>

    @Query("SELECT * FROM cards WHERE scryfall_id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<CardEntity>

    @Query("SELECT * FROM cards WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchByName(query: String): Flow<List<CardEntity>>

    @Query("SELECT COUNT(*) > 0 FROM cards WHERE scryfall_id = :id AND cached_at > :minCachedAt")
    suspend fun isCacheValid(id: String, minCachedAt: Long): Boolean

    @Query("""
        DELETE FROM cards
        WHERE scryfall_id NOT IN (SELECT scryfall_id FROM user_cards)
        AND scryfall_id NOT IN (SELECT scryfall_id FROM deck_cards)
        AND cached_at < :evictBefore
    """)
    suspend fun evictStaleCache(evictBefore: Long)

    @Query("UPDATE cards SET is_stale = 1, stale_reason = :reason WHERE scryfall_id = :id")
    suspend fun markStale(id: String, reason: String)

    @Query("UPDATE cards SET is_stale = 0, stale_reason = NULL WHERE scryfall_id = :id")
    suspend fun clearStale(id: String)

    @Query("SELECT * FROM cards WHERE is_stale = 1")
    fun observeStaleCards(): Flow<List<CardEntity>>

    @Query("UPDATE cards SET tags = :tagsJson WHERE scryfall_id = :scryfallId")
    suspend fun updateTags(scryfallId: String, tagsJson: String)

    @Query("UPDATE cards SET user_tags = :userTagsJson WHERE scryfall_id = :scryfallId")
    suspend fun updateUserTags(scryfallId: String, userTagsJson: String)

    @Query("UPDATE cards SET suggested_tags = :json WHERE scryfall_id = :scryfallId")
    suspend fun updateSuggestedTags(scryfallId: String, json: String)

    @Query("UPDATE cards SET tags = :tagsJson, suggested_tags = :suggestedJson WHERE scryfall_id = :scryfallId")
    suspend fun updateTagsAndSuggestions(scryfallId: String, tagsJson: String, suggestedJson: String)

    @Query("""
        UPDATE cards SET
            price_usd      = :priceUsd,
            price_usd_foil = :priceUsdFoil,
            price_eur      = :priceEur,
            price_eur_foil = :priceEurFoil,
            cached_at      = :updatedAt
        WHERE scryfall_id = :scryfallId
    """)
    suspend fun updatePrices(
        scryfallId:   String,
        priceUsd:     Double?,
        priceUsdFoil: Double?,
        priceEur:     Double?,
        priceEurFoil: Double?,
        updatedAt:    Long = System.currentTimeMillis(),
    )
}
