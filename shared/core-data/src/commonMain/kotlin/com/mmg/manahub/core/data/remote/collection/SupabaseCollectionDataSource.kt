package com.mmg.manahub.core.data.remote.collection

import com.mmg.manahub.core.common.DispatcherProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * Supabase implementation of [CollectionRemoteDataSource].
 *
 * Uses the Supabase SDK v3 RPC API. Key SDK gotchas applied here:
 * - `rpc("name", buildJsonObject { put("key", value) })` — params must be [kotlinx.serialization.json.JsonObject]
 * - `rpc().decodeList<T>()` — not `.body`
 * - All timestamps are Long (epoch millis), not Instant strings.
 *
 * KMP web roadmap W3d (master plan §5/§6): moved to `:shared:core-data` commonMain so Android's
 * `com.mmg.manahub.core.sync.SyncManager` and the web target's `WebUserCardRepository` share the
 * exact same Supabase-calling logic — no duplicated RPC-wiring class per platform (mirrors the
 * W3c move of `SupabaseDeckDataSource`). No `@Inject`/`@Singleton` (Hilt is androidMain-only):
 * Android provides this as a Hilt `@Provides @Singleton` in `SharedDomainUseCaseModule`
 * (mirroring the pre-existing `ScryfallRemoteDataSource`/`DeckRemoteDataSource` pattern),
 * `:webApp` registers it as a Koin `single`. [DispatcherProvider] replaces Hilt's `@IoDispatcher`
 * qualifier — its `io` property maps to `Dispatchers.IO` on Android and `Dispatchers.Default` on
 * wasmJs (no dedicated IO pool on the web).
 */
class SupabaseCollectionDataSource(
    private val supabaseClient: SupabaseClient,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider(),
) : CollectionRemoteDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Calls the `get_collection_changes_since` RPC.
     * The RPC uses `auth.uid()` internally for RLS row filtering.
     */
    override suspend fun getChangesSince(since: Long): Result<List<UserCardCollectionDto>> =
        withContext(dispatcherProvider.io) {
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
        withContext(dispatcherProvider.io) {
            runCatching {
                val jsonArray = json.encodeToJsonElement(rows)
                val params = buildJsonObject { put("p_rows", jsonArray) }
                supabaseClient.postgrest.rpc("batch_upsert_collection", params)
                Unit
            }
        }

    /**
     * Calls the `merge_collection_entry` RPC (added 2026-08-03 by `backend-supabase-expert`
     * specifically for this web slice — see [CollectionRemoteDataSource.mergeEntry]'s KDoc).
     * Returns `Result.success(false)` when the RPC itself reports the entry wasn't found/owned
     * by the caller — never throws for that expected case.
     */
    override suspend fun mergeEntry(
        entryId: String,
        newScryfallId: String,
        isFoil: Boolean,
        condition: String,
        language: String,
        quantity: Int,
    ): Result<Boolean> = withContext(dispatcherProvider.io) {
        runCatching {
            val params = buildJsonObject {
                put("p_entry_id", entryId)
                put("p_new_scryfall_id", newScryfallId)
                put("p_is_foil", isFoil)
                put("p_condition", condition)
                put("p_language", language)
                put("p_quantity", quantity)
            }
            supabaseClient.postgrest.rpc("merge_collection_entry", params).decodeAs<Boolean>()
        }
    }
}
