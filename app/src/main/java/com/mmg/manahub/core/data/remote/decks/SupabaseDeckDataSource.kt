package com.mmg.manahub.core.data.remote.decks

import com.mmg.manahub.core.di.IoDispatcher
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseDeckDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DeckRemoteDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getLastModified(): Instant? = withContext(ioDispatcher) {
        runCatching {
            supabaseClient.postgrest
                .rpc("get_deck_last_modified", buildJsonObject {})
                .decodeSingleOrNull<String>()
                ?.let { Instant.parse(it) }
        }.getOrNull()
    }

    override suspend fun upsertDeck(params: UpsertDeckParams): Result<String> =
        withContext(ioDispatcher) {
            runCatching {
                val body = json.encodeToJsonElement(params).jsonObject
                supabaseClient.postgrest
                    .rpc("upsert_deck", body)
                    .decodeSingleOrNull<String>() ?: ""
            }
        }

    override suspend fun deleteDeck(localDeckId: Long): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val body = buildJsonObject { put("p_local_id", localDeckId) }
                supabaseClient.postgrest.rpc("delete_deck", body)
                Unit
            }
        }

    override suspend fun upsertDeckCards(
        localDeckId: Long,
        cards: List<DeckCardDto>,
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val body = buildJsonObject {
                put("p_local_deck_id", localDeckId)
                put("p_cards", json.encodeToJsonElement(cards))
            }
            supabaseClient.postgrest.rpc("upsert_deck_cards", body)
            Unit
        }
    }

    override suspend fun getDecksChangedSince(since: Instant): Result<List<DeckDto>> =
        withContext(ioDispatcher) {
            runCatching {
                val body = buildJsonObject { put("p_since", since.toString()) }
                supabaseClient.postgrest
                    .rpc("get_decks_updated_since", body)
                    .decodeList<DeckDto>()
            }
        }
}
