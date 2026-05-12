package com.mmg.manahub.core.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.mmg.manahub.core.data.local.entity.DeckCardEntity
import com.mmg.manahub.core.data.local.entity.DeckEntity
import kotlinx.coroutines.flow.Flow

/** Flat row returned by the deck-summary JOIN — one row per deck x card combination. */
data class DeckSummaryRow(
    @ColumnInfo(name = "deckId") val deckId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "format") val format: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "coverCardId") val coverCardId: String?,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
    @ColumnInfo(name = "scryfallId") val scryfallId: String?,
    @ColumnInfo(name = "isSideboard") val isSideboard: Boolean,
    @ColumnInfo(name = "quantity") val quantity: Int,
    @ColumnInfo(name = "colorIdentity") val colorIdentity: String?,
    @ColumnInfo(name = "imageArtCrop") val imageArtCrop: String?,
)

@Dao
interface DeckDao {

    // ── Write operations ──────────────────────────────────────────────────────

    @Upsert
    fun upsertDeck(deck: DeckEntity)

    @Upsert
    fun upsertDeckCard(card: DeckCardEntity)

    @Upsert
    fun upsertDeckCards(cards: List<DeckCardEntity>)

    // Wipes all card rows for a deck before re-inserting — used when replacing
    // the full card list atomically (e.g. import, sync pull).
    @Query("DELETE FROM deck_cards WHERE deck_id = :deckId")
    fun clearDeckCards(deckId: String)

    @Query("DELETE FROM deck_cards WHERE deck_id = :deckId AND scryfall_id = :scryfallId AND is_sideboard = :isSideboard")
    fun removeDeckCard(deckId: String, scryfallId: String, isSideboard: Boolean)

    // Soft-delete: preserves the row so the tombstone can be synced to Supabase.
    @Query("UPDATE decks SET is_deleted = 1, updated_at = :updatedAt WHERE id = :deckId")
    fun softDeleteDeck(deckId: String, updatedAt: Long = System.currentTimeMillis())

    // Assigns real userId to all guest-owned decks on login.
    // Returns count of updated rows so caller can decide whether to trigger a sync.
    @Query("UPDATE decks SET user_id = :newUserId, updated_at = :updatedAt WHERE user_id IS NULL OR user_id = ''")
    fun assignDeckUserId(newUserId: String, updatedAt: Long = System.currentTimeMillis()): Int

    // ── Reactive read operations ───────────────────────────────────────────────

    // All non-deleted decks for this user, ordered most-recently-modified first.
    @Query("SELECT * FROM decks WHERE (user_id = :userId OR user_id IS NULL) AND is_deleted = 0 ORDER BY updated_at DESC")
    fun observeAllDecks(userId: String?): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE id = :deckId")
    fun observeDeckById(deckId: String): Flow<DeckEntity?>

    // @Transaction is required by Room whenever a @Relation is involved, to
    // prevent inconsistent reads across the two queries Room issues internally.
    @Transaction
    @Query("SELECT * FROM decks WHERE id = :deckId AND is_deleted = 0")
    fun observeDeckWithCards(deckId: String): Flow<DeckWithCards?>

    // ── Sync / pull operations ─────────────────────────────────────────────────

    // Returns all decks modified after :since — includes is_deleted = 1 rows
    // so tombstones are pushed to Supabase during incremental sync.
    @Query("SELECT * FROM decks WHERE (user_id = :userId OR user_id IS NULL) AND updated_at > :since")
    fun getDecksSince(userId: String, since: Long): List<DeckEntity>

    @Query("SELECT * FROM decks WHERE id = :deckId AND is_deleted = 0")
    fun getDeckById(deckId: String): DeckEntity?

    // Includes soft-deleted rows — used by SyncManager PULL to avoid falsely overwriting local tombstones.
    @Query("SELECT * FROM decks WHERE id = :deckId")
    fun getDeckByIdForSync(deckId: String): DeckEntity?

    @Query("SELECT * FROM deck_cards WHERE deck_id = :deckId")
    fun getDeckCards(deckId: String): List<DeckCardEntity>

    // Atomically replaces all card slots for a deck — used by the sync PULL phase.
    @Transaction
    fun replaceAllCards(deckId: String, cards: List<DeckCardEntity>) {
        clearDeckCards(deckId)
        if (cards.isNotEmpty()) upsertDeckCards(cards)
    }

    // ── Stats / other features ─────────────────────────────────────────────────

    @Query("""
        SELECT d.id            AS deckId,
               d.name          AS name,
               d.format        AS format,
               d.description   AS description,
               d.cover_card_id AS coverCardId,
               d.created_at    AS createdAt,
               d.updated_at    AS updatedAt,
               dc.scryfall_id  AS scryfallId,
               COALESCE(dc.is_sideboard, 0) AS isSideboard,
               COALESCE(dc.quantity, 0)     AS quantity,
               c.color_identity AS colorIdentity,
               c.image_art_crop AS imageArtCrop
        FROM decks d
        LEFT JOIN deck_cards dc ON d.id = dc.deck_id
        LEFT JOIN cards c ON dc.scryfall_id = c.scryfall_id
        WHERE d.is_deleted = 0
        ORDER BY d.updated_at DESC
    """)
    fun observeDeckSummaryRows(): Flow<List<DeckSummaryRow>>

    @Query("SELECT COUNT(*) FROM decks WHERE is_deleted = 0")
    fun observeDeckCount(): Flow<Int>

    @Query("""
        SELECT d.* FROM decks d
        INNER JOIN deck_cards dc ON d.id = dc.deck_id
        WHERE dc.scryfall_id = :scryfallId AND d.is_deleted = 0
        ORDER BY d.updated_at DESC
    """)
    fun observeDecksContainingCard(scryfallId: String): Flow<List<DeckEntity>>

    @Query("SELECT MAX(updated_at) FROM decks")
    fun getMaxUpdatedAt(): Long?
}

data class DeckWithCards(
    @Embedded val deck: DeckEntity,
    @Relation(parentColumn = "id", entityColumn = "deck_id", entity = DeckCardEntity::class)
    val cards: List<DeckCardEntity>
)
