package com.mmg.manahub.core.data.remote.trades

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.model.SharedList
import com.mmg.manahub.core.model.SharedListResult
import com.mmg.manahub.core.model.SharedListType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Remote data source for shared lists (wishlist / open-for-trade).
 *
 * All calls delegate to [SupabaseClient] PostgREST and run on [DispatcherProvider.io]
 * (KMP-safe replacement for `Dispatchers.IO`).
 *
 * @param supabaseClient      The Supabase client for PostgREST calls.
 * @param dispatcherProvider   Platform dispatcher abstraction.
 */
class SharedListsRemoteDataSource(
    private val supabaseClient: SupabaseClient,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createSharedList(userId: String, listType: SharedListType): Result<SharedList> =
        withContext(dispatcherProvider.io) {
            runCatching {
                val row = buildJsonObject {
                    put("user_id", userId)
                    put("list_type", listType.name)
                }
                supabaseClient.postgrest["shared_lists"].insert(row)
                // re-fetch latest entry for this user+type to get the generated id
                supabaseClient.postgrest["shared_lists"]
                    .select {
                        filter {
                            eq("user_id", userId)
                            eq("list_type", listType.name)
                        }
                        limit(1)
                        order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    }
                    .decodeSingle<SharedListRowDto>()
                    .toDomain()
            }
        }

    suspend fun resolveSharedList(shareId: String): Result<SharedListResult> =
        withContext(dispatcherProvider.io) {
            runCatching {
                val raw = supabaseClient.postgrest
                    .rpc("resolve_shared_list", buildJsonObject { put("p_share_id", shareId) })
                    .decodeSingle<String>()
                val obj = json.parseToJsonElement(raw).jsonObject
                when (obj["status"]?.jsonPrimitive?.content) {
                    "private" -> SharedListResult.Private
                    "not_found" -> SharedListResult.NotFound
                    else -> {
                        val typeStr = obj["list_type"]?.jsonPrimitive?.content ?: "WISHLIST"
                        val ownerId = obj["user_id"]?.jsonPrimitive?.content ?: ""
                        val ownerNickname = obj["owner_nickname"]?.jsonPrimitive?.content ?: ""
                        SharedListResult.Ok(
                            listType = SharedListType.valueOf(typeStr),
                            userId = ownerId,
                            ownerNickname = ownerNickname,
                            items = emptyList(),
                        )
                    }
                }
            }
        }

    @kotlinx.serialization.Serializable
    private data class SharedListRowDto(
        val id: String,
        @kotlinx.serialization.SerialName("user_id") val userId: String,
        @kotlinx.serialization.SerialName("list_type") val listType: String,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String,
    ) {
        fun toDomain() = SharedList(
            id = id,
            userId = userId,
            listType = SharedListType.valueOf(listType),
            createdAt = 0L,
        )
    }
}
