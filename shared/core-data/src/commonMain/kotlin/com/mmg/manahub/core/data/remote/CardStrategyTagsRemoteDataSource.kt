package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.remote.dto.CardStrategyTagsPayloadDto
import com.mmg.manahub.core.data.remote.dto.CardStrategyTagsRowDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

private val submitPayloadJson = Json { encodeDefaults = true }

/**
 * Narrow contract over the Supabase `card_strategy_tags` table lookup — mirrors
 * [com.mmg.manahub.core.data.remote.CommunityAggregateApiContract]'s "interface, not the concrete
 * client class" pattern so [com.mmg.manahub.core.data.repository.CardStrategyTagsRepositoryImpl]
 * is unit-testable with a trivial hand-written fake, with no Supabase/Postgrest client mock needed.
 */
interface CardStrategyTagsRemoteDataSourceContract {
    /** Returns the row for [oracleId], or null when no row exists yet (not an error). */
    suspend fun getByOracleId(oracleId: String): CardStrategyTagsRowDto?

    /**
     * Calls the `submit_card_strategy_tags(p_oracle_id, p_payload)` RPC (plan §8a addendum) — a
     * `SECURITY DEFINER` function that INSERTs [payload] only when no row exists yet for
     * [oracleId] (`ON CONFLICT (oracle_id) DO NOTHING`, `pipeline_version` hardcoded server-side
     * to `'device'`). Throws on a genuine RPC failure (network/auth/rate-limit/validation
     * rejection) — callers (`CardStrategyTagsRepositoryImpl`) are responsible for catching and
     * logging, never propagating.
     */
    suspend fun submit(oracleId: String, payload: CardStrategyTagsPayloadDto)
}

/**
 * Real Supabase-backed implementation. Table is SELECT-open to `anon`+`authenticated` (guests and
 * signed-in users both see auto-generated tags on any card, per plan D8/5c) — no auth-gating logic
 * needed here for reads. [submit] requires an active session (real or anon-auth guest) server-side
 * — the RPC itself rejects an unauthenticated caller.
 */
class CardStrategyTagsRemoteDataSource(
    private val supabaseClient: SupabaseClient,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider(),
) : CardStrategyTagsRemoteDataSourceContract {

    override suspend fun getByOracleId(oracleId: String): CardStrategyTagsRowDto? =
        withContext(dispatcherProvider.io) {
            supabaseClient.postgrest["card_strategy_tags"]
                .select { filter { eq("oracle_id", oracleId) } }
                .decodeSingleOrNull<CardStrategyTagsRowDto>()
        }

    override suspend fun submit(oracleId: String, payload: CardStrategyTagsPayloadDto) {
        withContext(dispatcherProvider.io) {
            val params = buildJsonObject {
                put("p_oracle_id", oracleId)
                put("p_payload", submitPayloadJson.encodeToJsonElement(CardStrategyTagsPayloadDto.serializer(), payload))
            }
            supabaseClient.postgrest.rpc("submit_card_strategy_tags", params)
        }
    }
}
