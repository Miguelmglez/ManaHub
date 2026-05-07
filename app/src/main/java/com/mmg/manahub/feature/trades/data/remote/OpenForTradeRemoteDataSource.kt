package com.mmg.manahub.feature.trades.data.remote

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.trades.data.remote.dto.OpenForTradeEntryDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenForTradeRemoteDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun getOpenForTrade(userId: String): Result<List<OpenForTradeEntryDto>> =
        withContext(ioDispatcher) {
            runCatching {
                supabaseClient.postgrest["open_for_trade"]
                    .select { filter { eq("user_id", userId) } }
                    .decodeList<OpenForTradeEntryDto>()
            }
        }

    suspend fun addOpenForTradeEntry(userCardId: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                supabaseClient.postgrest["open_for_trade"].insert(
                    buildJsonObject { put("user_card_id", userCardId) }
                )
                Unit
            }
        }

    suspend fun removeOpenForTradeEntry(id: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                supabaseClient.postgrest["open_for_trade"]
                    .delete { filter { eq("id", id) } }
                Unit
            }
        }

    suspend fun batchAddOpenForTradeEntries(userCardIds: List<String>): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                if (userCardIds.isEmpty()) return@runCatching
                val rows = userCardIds.map { buildJsonObject { put("user_card_id", it) } }
                supabaseClient.postgrest["open_for_trade"].insert(rows)
                Unit
            }
        }
}
