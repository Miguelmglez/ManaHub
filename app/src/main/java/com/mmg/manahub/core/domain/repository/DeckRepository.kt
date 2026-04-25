package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.model.DeckSummary
import com.mmg.manahub.core.domain.model.DeckWithCards
import kotlinx.coroutines.flow.Flow

/**
 * Contract for all deck persistence operations.
 *
 * Sync is NOT part of this interface. The [com.mmg.manahub.core.sync.SyncManager]
 * owns the push/pull cycle. This repository is responsible only for local CRUD.
 * All mutations update [Deck.updatedAt] so the sync engine can detect dirty rows.
 */
interface DeckRepository {

    // ── Observables ───────────────────────────────────────────────────────────

    /** Emits all non-deleted decks ordered by most recently updated. */
    fun observeAllDecks(): Flow<List<Deck>>

    /** Emits deck summaries (with card count and color identity) for the list view. */
    fun observeAllDeckSummaries(): Flow<List<DeckSummary>>

    /** Emits decks that contain a specific Scryfall card. */
    fun observeDecksContainingCard(scryfallId: String): Flow<List<Deck>>

    /** Emits a single deck with all its card slots (mainboard + sideboard). */
    fun observeDeckWithCards(deckId: String): Flow<DeckWithCards?>

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Creates a new deck locally. Generates a UUID client-side and sets
     * [Deck.updatedAt] so the next sync push uploads it automatically.
     *
     * @return The UUID of the newly created deck.
     */
    suspend fun createDeck(name: String, description: String, format: String): String

    /**
     * Updates deck metadata. Bumps [Deck.updatedAt] so the sync engine picks up the change.
     */
    suspend fun updateDeck(deck: Deck)

    /**
     * Soft-deletes the deck. Sets `isDeleted = true` and bumps `updatedAt`.
     * Does NOT physically remove the row so the deletion is propagated on the next sync.
     */
    suspend fun deleteDeck(deckId: String)

    /** Adds or updates a card slot in the deck's mainboard or sideboard. */
    suspend fun addCardToDeck(deckId: String, scryfallId: String, quantity: Int = 1, isSideboard: Boolean = false)

    /** Removes a card slot from the deck. */
    suspend fun removeCardFromDeck(deckId: String, scryfallId: String, isSideboard: Boolean)

    /** Removes all card slots from the deck (does NOT delete the deck itself). */
    suspend fun clearDeck(deckId: String)

    /**
     * Atomically replaces the entire card list for a deck.
     * Used by [saveDeck] in the ViewModel to flush the in-memory draft to Room in one transaction.
     * @param slots List of (scryfallId, quantity, isSideboard) triples.
     */
    suspend fun replaceAllCards(deckId: String, slots: List<Triple<String, Int, Boolean>>)
}
