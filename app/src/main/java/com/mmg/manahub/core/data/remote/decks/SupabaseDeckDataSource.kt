package com.mmg.manahub.core.data.remote.decks

import com.mmg.manahub.core.di.IoDispatcher
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of [DeckRemoteDataSource].
 *
 * Uses the Supabase SDK v3 RPC API. Key SDK gotchas applied here:
 * - `rpc("name", buildJsonObject { put("key", value) })` — params must be [kotlinx.serialization.json.JsonObject]
 * - `rpc().decodeList<T>()` — not `.body`
 * - All timestamps are Long (epoch millis), not Instant strings.
 */
@Singleton
class SupabaseDeckDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DeckRemoteDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Calls `get_deck_changes_since` RPC.
     * The RPC uses `auth.uid()` internally for RLS row filtering.
     */
    override suspend fun getDeckChangesSince(since: Long): Result<List<DeckSyncDto>> =
        withContext(ioDispatcher) {
            runCatching {
                val params = buildJsonObject { put("p_since", since) }
                supabaseClient.postgrest
                    .rpc("get_deck_changes_since", params)
                    .decodeList<DeckSyncDto>()
            }
        }

    /**
     * Serializes [rows] to a JSON array and calls `batch_upsert_decks`.
     * The RPC performs server-side upsert on the `id` primary key.
     */
    override suspend fun batchUpsertDecks(rows: List<DeckSyncDto>): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val jsonArray = json.encodeToJsonElement(rows)
                val params = buildJsonObject { put("p_rows", jsonArray) }
                supabaseClient.postgrest.rpc("batch_upsert_decks", params)
                Unit
            }
        }

    /**
     * Serializes [cards] to a JSON array and calls `upsert_deck_cards`.
     * The RPC performs a full replacement of all cards for [deckId].
     */
    override suspend fun upsertDeckCards(deckId: String, cards: List<DeckCardSyncDto>): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val jsonArray = json.encodeToJsonElement(cards)
                val params = buildJsonObject {
                    put("p_deck_id", deckId)
                    put("p_cards", jsonArray)
                }
                supabaseClient.postgrest.rpc("upsert_deck_cards", params)
                Unit
            }
        }

    /**
     * Calls `get_deck_cards_for_deck` RPC to fetch all card slots for a single deck.
     * Used during the PULL phase so card slots are restored alongside deck metadata.
     */
    override suspend fun getDeckCardsForDeck(deckId: String): Result<List<DeckCardSyncDto>> =
        withContext(ioDispatcher) {
            runCatching {
                val params = buildJsonObject { put("p_deck_id", deckId) }
                supabaseClient.postgrest
                    .rpc("get_deck_cards_for_deck", params)
                    .decodeList<DeckCardSyncDto>()
            }
        }
}
