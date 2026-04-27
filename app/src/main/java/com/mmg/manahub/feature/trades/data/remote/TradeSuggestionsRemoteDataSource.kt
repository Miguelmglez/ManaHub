package com.mmg.manahub.feature.trades.data.remote

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.trades.data.remote.dto.TradeSuggestionDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TradeSuggestionsRemoteDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun getSuggestions(): Result<List<TradeSuggestionDto>> =
        withContext(ioDispatcher) {
            runCatching {
                supabaseClient.postgrest
                    .rpc("get_trade_suggestions", buildJsonObject {})
                    .decodeList<TradeSuggestionDto>()
            }
        }

    suspend fun getSuggestionsForCard(cardId: String): Result<List<TradeSuggestionDto>> =
        withContext(ioDispatcher) {
            runCatching {
                supabaseClient.postgrest
                    .rpc("get_suggestions_for_card", buildJsonObject { put("p_card_id", cardId) })
                    .decodeList<TradeSuggestionDto>()
            }
        }

    suspend fun refreshSuggestions(): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                supabaseClient.postgrest.rpc("refresh_trade_suggestions", buildJsonObject {})
                Unit
            }
        }
}
