package com.mmg.manahub.core.data.remote.trades

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.remote.dto.OpenForTradeEntryDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Remote data source for the "open for trade" entries.
 *
 * All calls delegate to [SupabaseClient] PostgREST and run on [DispatcherProvider.io]
 * (KMP-safe replacement for `Dispatchers.IO`).
 *
 * @param supabaseClient      The Supabase client for PostgREST calls.
 * @param dispatcherProvider   Platform dispatcher abstraction.
 */
class OpenForTradeRemoteDataSource(
    private val supabaseClient: SupabaseClient,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider(),
) {
    /** Every row of [userId]'s list; fails unless the whole list was fetched. */
    suspend fun getOpenForTrade(userId: String): Result<List<OpenForTradeEntryDto>> = drainOpenForTrade(userId).toResult()

    /**
     * Every row of [userId]'s list, drained page by page in `(created_at, id)` order. A table
     * query past `db-max-rows` is silently truncated, so only a complete drain may drive eviction.
     */
    suspend fun drainOpenForTrade(userId: String): KeysetDrain<OpenForTradeEntryDto> =
        drainByCreatedAt(
            id = { it.id },
            createdAt = { it.createdAt },
        ) { after, limit -> getOpenForTradePage(userId, after, limit) }

    /** One `(created_at, id)` keyset page of [getOpenForTrade]. */
    suspend fun getOpenForTradePage(userId: String, after: CreatedAtCursor?, limit: Int): Result<List<OpenForTradeEntryDto>> =
        dispatcherProvider.remoteResult {
            supabaseClient.postgrest["open_for_trade_with_card"]
                .select {
                    filter {
                        eq("user_id", userId)
                        if (after != null) afterCreatedAt(after)
                    }
                    createdAtPage(limit)
                }
                .decodeList<OpenForTradeEntryDto>()
        }

    suspend fun addOpenForTradeEntry(userCardId: String): Result<Unit> =
        dispatcherProvider.remoteResult {
            supabaseClient.postgrest["open_for_trade"].insert(
                buildJsonObject { put("user_card_id", userCardId) }
            )
            Unit
        }

    suspend fun removeOpenForTradeEntry(id: String): Result<Unit> =
        dispatcherProvider.remoteResult {
            supabaseClient.postgrest["open_for_trade"]
                .delete { filter { eq("id", id) } }
            Unit
        }

    suspend fun removeByUserCardId(userCardId: String): Result<Unit> =
        dispatcherProvider.remoteResult {
            supabaseClient.postgrest["open_for_trade"]
                .delete { filter { eq("user_card_id", userCardId) } }
            Unit
        }

    suspend fun batchAddOpenForTradeEntries(userCardIds: List<String>): Result<Unit> =
        dispatcherProvider.remoteResult {
            if (userCardIds.isEmpty()) return@remoteResult Unit
            val rows = userCardIds.map { buildJsonObject { put("user_card_id", it) } }
            // upsert prevents duplicate-key failures when entries already exist in Supabase.
            supabaseClient.postgrest["open_for_trade"].upsert(rows)
            Unit
        }
}
