package com.mmg.manahub.core.data.remote.decks

/**
 * Contract for all Supabase operations on the `decks` and `deck_cards` tables.
 *
 * Sync is driven by [updatedAt] epoch millis (Last-Write-Wins).
 * All methods return [Result] so callers can handle failures without try/catch.
 *
 * KMP web roadmap W3c (master plan §5/§6): moved from `:app` (androidMain-equivalent) to
 * `:shared:core-data` commonMain so both Android's [com.mmg.manahub.core.sync.SyncManager]
 * (Room-facing push/pull) and the web target's `WebDeckRepository` (remote-first, no local
 * database) can share ONE contract for talking to the `decks`/`deck_cards` Supabase RPCs. The
 * Room-facing entity mapping extensions ([toEntity]-equivalent) stay in `:app` since Room has no
 * wasmJs target — only the pure Supabase-calling contract and its DTOs moved here.
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
     * Keyset-paginated counterpart of [getDeckChangesSince] (collection sync data-loss fix,
     * `linear-moseying-yeti` plan, Phase 1/3) — see [com.mmg.manahub.core.data.remote.collection
     * .CollectionRemoteDataSource.getChangesPage]'s KDoc for the full rationale and cursor
     * contract; this is the same shape over `get_deck_changes_page`.
     */
    suspend fun getDeckChangesPage(
        since: Long,
        afterUpdatedAt: Long? = null,
        afterId: String? = null,
        limit: Int = 500,
    ): Result<List<DeckSyncDto>>

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

    /**
     * Fetches all card slots for a single deck from Supabase.
     *
     * Delegates to the `get_deck_cards_for_deck` RPC, which enforces RLS via `auth.uid()`.
     * Used during the sync PULL phase to restore card slots alongside deck metadata.
     *
     * @param deckId UUID of the deck whose cards should be fetched.
     */
    suspend fun getDeckCardsForDeck(deckId: String): Result<List<DeckCardSyncDto>>
}
