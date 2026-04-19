package com.mmg.manahub.core.data.remote.decks

import java.time.Instant

interface DeckRemoteDataSource {
    /** Returns the MAX(updated_at) across all non-deleted decks for the current user, or null. */
    suspend fun getLastModified(): Instant?

    /** Upserts a deck via the `upsert_deck` RPC. Returns the Supabase-assigned UUID. */
    suspend fun upsertDeck(params: UpsertDeckParams): Result<String>

    /** Soft-deletes a deck by local Room id via the `delete_deck` RPC. */
    suspend fun deleteDeck(localDeckId: Long): Result<Unit>

    /** Replaces all cards for a deck atomically via the `upsert_deck_cards` RPC. */
    suspend fun upsertDeckCards(localDeckId: Long, cards: List<DeckCardDto>): Result<Unit>

    /** Returns all non-deleted decks updated after [since] for the current user. */
    suspend fun getDecksChangedSince(since: Instant): Result<List<DeckDto>>
}
