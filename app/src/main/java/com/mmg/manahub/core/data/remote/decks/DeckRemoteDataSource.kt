package com.mmg.manahub.core.data.remote.decks

/**
 * Contract for all Supabase operations on the `decks` and `deck_cards` tables.
 *
 * Sync is driven by [updatedAt] epoch millis (Last-Write-Wins).
 * All methods return [Result] so callers can handle failures without try/catch.
 */
interface DeckRemoteDataSource {

    /**
     * Fetches all deck rows (including soft-deleted) modified after [since].
     *
     * Delegates to the `get_deck_changes_since` Supabase RPC.
     * The RPC uses `auth.uid()` server-side for RLS — no explicit user_id param needed.
     *
     * @param since Epoch millis watermark; pass 0L for a full pull.
     */
    suspend fun getDeckChangesSince(since: Long): Result<List<DeckSyncDto>>

    /**
     * Upserts a batch of deck rows using the `batch_upsert_decks` RPC.
     *
     * Deck metadata only — call [upsertDeckCards] separately for card slots.
     *
     * @param rows List of [DeckSyncDto] to upload.
     */
    suspend fun batchUpsertDecks(rows: List<DeckSyncDto>): Result<Unit>

    /**
     * Replaces all card slots for a single deck using the `upsert_deck_cards` RPC.
     *
     * The RPC performs a full replacement (DELETE + INSERT) server-side so that
     * removed cards are cleaned up atomically.
     *
     * @param deckId UUID of the deck whose cards are being replaced.
     * @param cards  New list of card slots for the deck.
     */
    suspend fun upsertDeckCards(deckId: String, cards: List<DeckCardSyncDto>): Result<Unit>
}
