package com.mmg.manahub.core.data.remote.collection

import com.mmg.manahub.core.di.IoDispatcher
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseCollectionDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CollectionRemoteDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getLastModified(userId: String): Instant? = withContext(ioDispatcher) {
        runCatching {
            // Fetch the single most-recently updated row's timestamp (DESC + limit 1 = MAX).
            supabaseClient.from(TABLE)
                .select {
                    filter { eq("user_id", userId) }
                    order("updated_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<UpdatedAtDto>()
                ?.updatedAt
                ?.let { Instant.parse(it) }
        }.getOrNull()
    }

    override suspend fun getChangesSince(
        userId: String,
        since: Instant,
    ): Result<List<UserCardCollectionDto>> = withContext(ioDispatcher) {
        runCatching {
            // RLS ensures only our own rows; the explicit user_id filter is defence-in-depth.
            supabaseClient.from(TABLE)
                .select {
                    filter {
                        eq("user_id", userId)
                        gt("updated_at", since.toString())
                    }
                    order("updated_at", Order.ASCENDING)
                }
                .decodeList<UserCardCollectionDto>()
        }
    }

    override suspend fun upsertCard(params: UpsertUserCardParams): Result<String> =
        withContext(ioDispatcher) {
            runCatching {
                // Use the upsert_user_card RPC to atomically insert-or-update on the unique
                // constraint (user_id, scryfall_id, is_foil, condition, language, ...).
                val jsonParams = json.encodeToJsonElement(params).jsonObject
                val remoteId = supabaseClient.postgrest
                    .rpc("upsert_user_card", jsonParams)
                    .decodeSingleOrNull<String>()
                remoteId ?: ""
            }
        }

    override suspend fun softDeleteCard(remoteId: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                supabaseClient.from(TABLE)
                    .update(SoftDeletePayload()) {
                        filter { eq("id", remoteId) }
                    }
                Unit
            }
        }

    private companion object {
        const val TABLE = "user_card_collection"
    }
}

@kotlinx.serialization.Serializable
private data class UpdatedAtDto(
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String?,
)
