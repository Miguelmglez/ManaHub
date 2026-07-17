package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mmg.manahub.core.data.local.entity.CardEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CardDao {

    // ── Safe upsert: INSERT OR IGNORE + UPDATE ────────────────────────────────
    // Using OnConflictStrategy.REPLACE would internally DELETE then INSERT the
    // CardEntity row, which triggers the CASCADE DELETE FK on user_cards and
    // silently wipes every UserCardEntity that references the refreshed card.
    // INSERT OR IGNORE + @Update avoids the delete entirely.

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(card: CardEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAllIgnore(cards: List<CardEntity>): List<Long>

    @Update
    abstract suspend fun updateCard(card: CardEntity)

    @Update
    abstract suspend fun updateAllCards(cards: List<CardEntity>)

    @Transaction
    open suspend fun upsert(card: CardEntity) {
        val id = insertIgnore(card)
        if (id == -1L) updateCard(card)
    }

    @Transaction
    open suspend fun upsertAll(cards: List<CardEntity>) {
        val ids = insertAllIgnore(cards)
        val toUpdate = cards.filterIndexed { index, _ -> ids[index] == -1L }
        if (toUpdate.isNotEmpty()) updateAllCards(toUpdate)
    }

    @Query("SELECT * FROM cards WHERE scryfall_id = :id")
    abstract suspend fun getById(id: String): CardEntity?

    @Query("SELECT * FROM cards WHERE scryfall_id = :id")
    abstract fun observeById(id: String): Flow<CardEntity?>

    @Query("SELECT * FROM cards WHERE scryfall_id IN (:ids)")
    abstract suspend fun getByIds(ids: List<String>): List<CardEntity>

    @Query("SELECT * FROM cards WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    abstract fun searchByName(query: String): Flow<List<CardEntity>>

    @Query("SELECT COUNT(*) > 0 FROM cards WHERE scryfall_id = :id AND cached_at > :minCachedAt")
    abstract suspend fun isCacheValid(id: String, minCachedAt: Long): Boolean

    @Query("""
        DELETE FROM cards
        WHERE scryfall_id NOT IN (SELECT scryfall_id FROM user_card_collection)
        AND scryfall_id NOT IN (SELECT scryfall_id FROM deck_cards)
        AND cached_at < :evictBefore
    """)
    abstract suspend fun evictStaleCache(evictBefore: Long)

    @Query("UPDATE cards SET is_stale = 1, stale_reason = :reason WHERE scryfall_id = :id")
    abstract suspend fun markStale(id: String, reason: String)

    @Query("UPDATE cards SET is_stale = 0, stale_reason = NULL WHERE scryfall_id = :id")
    abstract suspend fun clearStale(id: String)

    @Query("SELECT * FROM cards WHERE is_stale = 1")
    abstract fun observeStaleCards(): Flow<List<CardEntity>>

    // Edge-case audit A3 (2026-07-15). Feeds CardRepositoryImpl.backfillMissingOracleIds: finds
    // cards referenced by a LIVE collection row OR any wishlist row whose cached row predates the
    // oracle_id column (oracle_id = ''), capped at [limit]. A single cross-table query (rather
    // than fetching separate scryfall_id lists in Kotlin and filtering) — Room queries operate at
    // the DB level regardless of which Dao interface declares them, so this needs no new
    // cross-feature DAO dependency in CardRepositoryImpl.
    @Query("""
        SELECT scryfall_id FROM cards
        WHERE oracle_id = ''
          AND (
              scryfall_id IN (SELECT scryfall_id FROM user_card_collection WHERE is_deleted = 0)
              OR scryfall_id IN (SELECT scryfall_id FROM local_wishlists)
          )
        LIMIT :limit
    """)
    abstract suspend fun getScryfallIdsWithBlankOracleId(limit: Int): List<String>

    @Query("UPDATE cards SET tags = :tagsJson WHERE scryfall_id = :scryfallId")
    abstract suspend fun updateTags(scryfallId: String, tagsJson: String)

    @Query("UPDATE cards SET user_tags = :userTagsJson WHERE scryfall_id = :scryfallId")
    abstract suspend fun updateUserTags(scryfallId: String, userTagsJson: String)

    @Query("UPDATE cards SET suggested_tags = :json WHERE scryfall_id = :scryfallId")
    abstract suspend fun updateSuggestedTags(scryfallId: String, json: String)

    @Query("UPDATE cards SET tags = :tagsJson, suggested_tags = :suggestedJson WHERE scryfall_id = :scryfallId")
    abstract suspend fun updateTagsAndSuggestions(scryfallId: String, tagsJson: String, suggestedJson: String)

    // Broken-image fix (2026-07-17). Batch lookup of cached ENGLISH printings for a set of
    // (set_code, collector_number) pairs — feeds CardRepositoryImpl.getCachedEnglishSiblings,
    // which overrides a non-English collection group's image with its English sibling's (many
    // non-English Scryfall printings have no native image; same set + collector number always
    // shares the same illustration). Room has no tuple-IN support, so this over-fetches on the
    // cross product of the two IN lists and the caller filters down to exact pairs in Kotlin —
    // cheap for the small batches involved (distinct non-English printings in one collection load).
    @Query("""
        SELECT * FROM cards
        WHERE lang = 'en'
          AND set_code IN (:setCodes)
          AND collector_number IN (:collectorNumbers)
    """)
    abstract suspend fun getEnglishSiblings(setCodes: List<String>, collectorNumbers: List<String>): List<CardEntity>

    @Query("""
        UPDATE cards SET
            price_usd      = :priceUsd,
            price_usd_foil = :priceUsdFoil,
            price_eur      = :priceEur,
            price_eur_foil = :priceEurFoil,
            cached_at      = :updatedAt
        WHERE scryfall_id = :scryfallId
    """)
    abstract suspend fun updatePrices(
        scryfallId:   String,
        priceUsd:     Double?,
        priceUsdFoil: Double?,
        priceEur:     Double?,
        priceEurFoil: Double?,
        updatedAt:    Long,
    )
}
