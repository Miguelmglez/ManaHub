package com.mmg.manahub.core.data.remote.collection

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
 * Supabase implementation of [CollectionRemoteDataSource].
 *
 * Uses the Supabase SDK v3 RPC API. Key SDK gotchas applied here:
 * - `rpc("name", buildJsonObject { put("key", value) })` — params must be [kotlinx.serialization.json.JsonObject]
 * - `rpc().decodeList<T>()` — not `.body`
 * - All timestamps are Long (epoch millis), not Instant strings.
 */
@Singleton
class SupabaseCollectionDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CollectionRemoteDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Calls the `get_collection_changes_since` RPC.
     * The RPC uses `auth.uid()` internally for RLS row filtering.
     */
    override suspend fun getChangesSince(since: Long): Result<List<UserCardCollectionDto>> =
        withContext(ioDispatcher) {
            runCatching {
                val params = buildJsonObject { put("p_since", since) }
                supabaseClient.postgrest
                    .rpc("get_collection_changes_since", params)
                    .decodeList<UserCardCollectionDto>()
            }
        }

    /**
     * Serializes [rows] to a JSON array and calls `batch_upsert_collection`.
     * The RPC performs server-side upsert on the `id` primary key.
     */
    override suspend fun batchUpsert(rows: List<UserCardCollectionDto>): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val jsonArray = json.encodeToJsonElement(rows)
                val params = buildJsonObject { put("p_rows", jsonArray) }
                supabaseClient.postgrest.rpc("batch_upsert_collection", params)
                Unit
            }
        }
}
