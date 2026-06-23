package com.mmg.manahub.core.data.remote.trades

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.remote.dto.TradeSuggestionDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Remote data source for trade suggestions.
 *
 * All calls delegate to [SupabaseClient] PostgREST and run on [DispatcherProvider.io]
 * (KMP-safe replacement for `Dispatchers.IO`).
 *
 * @param supabaseClient      The Supabase client for PostgREST calls.
 * @param dispatcherProvider   Platform dispatcher abstraction.
 */
class TradeSuggestionsRemoteDataSource(
    private val supabaseClient: SupabaseClient,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider(),
) {
    suspend fun getSuggestions(): Result<List<TradeSuggestionDto>> =
        withContext(dispatcherProvider.io) {
            runCatching {
                supabaseClient.postgrest
                    .rpc("get_trade_suggestions", buildJsonObject {})
                    .decodeList<TradeSuggestionDto>()
            }
        }

    suspend fun getSuggestionsForCard(cardId: String): Result<List<TradeSuggestionDto>> =
        withContext(dispatcherProvider.io) {
            runCatching {
                supabaseClient.postgrest
                    .rpc("get_suggestions_for_card", buildJsonObject { put("p_card_id", cardId) })
                    .decodeList<TradeSuggestionDto>()
            }
        }

    suspend fun refreshSuggestions(): Result<Unit> =
        withContext(dispatcherProvider.io) {
            runCatching {
                supabaseClient.postgrest.rpc("refresh_trade_suggestions", buildJsonObject {})
                Unit
            }
        }
}
