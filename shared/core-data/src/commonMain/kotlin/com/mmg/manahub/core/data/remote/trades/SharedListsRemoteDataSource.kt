package com.mmg.manahub.core.data.remote.trades

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.model.SharedList
import com.mmg.manahub.core.model.SharedListResult
import com.mmg.manahub.core.model.SharedListType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import com.mmg.manahub.core.model.SharedListItem
import com.mmg.manahub.core.model.isValidShareId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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
    suspend fun createSharedList(userId: String, listType: SharedListType): Result<SharedList> =
        dispatcherProvider.remoteResult {
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

    suspend fun resolveSharedList(shareId: String): Result<SharedListResult> {
        // The RPC takes a uuid; any other id can only ever be "not found", so skip the round trip.
        if (!isValidShareId(shareId)) return Result.success(SharedListResult.NotFound)
        return dispatcherProvider.remoteResult {
            val obj = supabaseClient.postgrest
                .rpc("resolve_shared_list", buildJsonObject { put("p_share_id", shareId) })
                .decodeAs<JsonObject>()
            parseSharedListResult(obj)
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

/** Maps the `resolve_shared_list` jsonb payload; unknown list types and malformed items degrade to NotFound / skipped rows. */
internal fun parseSharedListResult(obj: JsonObject): SharedListResult =
    when (obj.string("status")) {
        "ok" -> {
            val listType = obj.string("list_type")?.let { type -> SharedListType.entries.firstOrNull { it.name == type } }
            if (listType == null) {
                SharedListResult.NotFound
            } else {
                SharedListResult.Ok(
                    listType = listType,
                    userId = obj.string("user_id").orEmpty(),
                    ownerNickname = obj.string("owner_nickname").orEmpty(),
                    items = (obj["items"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toSharedListItem() },
                )
            }
        }
        "private" -> SharedListResult.Private
        else -> SharedListResult.NotFound
    }

private fun JsonObject.toSharedListItem(): SharedListItem? {
    val cardId = string("card_id")?.takeIf { it.isNotBlank() } ?: return null
    return SharedListItem(
        cardId = cardId,
        quantity = (this["quantity"] as? JsonPrimitive)?.intOrNull?.coerceAtLeast(1) ?: 1,
        isFoil = (this["is_foil"] as? JsonPrimitive)?.booleanOrNull,
        condition = string("condition"),
        language = string("language"),
        matchAnyVariant = (this["match_any_variant"] as? JsonPrimitive)?.booleanOrNull ?: false,
        userCardId = string("user_card_id"),
    )
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
