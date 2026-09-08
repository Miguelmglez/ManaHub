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
 * Hard ceiling on ids per [CardStrategyTagsRemoteDataSourceContract.getByOracleIds] request.
 *
 * PostgREST silently truncates any response at `db-max-rows` (1000) with no signal to the client
 * (ADR-008 / `feedback_setof_rpc_must_be_paginated`) — a request that could return more rows than
 * that would lose the excess invisibly. Callers chunk far below the ceiling
 * ([CARD_STRATEGY_TAGS_ORACLE_ID_CHUNK]); this constant is the backstop that turns a future
 * over-large call into an immediate programming error instead of silent data loss.
 */
const val CARD_STRATEGY_TAGS_MAX_IDS_PER_REQUEST = 500

/** Default chunk size for a batched oracle-id read — kept small enough that the resulting
 *  `oracle_id=in.(...)` query string (36 chars per UUID) stays well inside any proxy URL-length
 *  limit, while still collapsing ~1300 cards into ~13 requests instead of 1300. */
const val CARD_STRATEGY_TAGS_ORACLE_ID_CHUNK = 100

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
     * Batched sibling of [getByOracleId] — one request for up to
     * [CARD_STRATEGY_TAGS_MAX_IDS_PER_REQUEST] ids, for bulk hydration of a whole collection
     * (ADR-005: ~13 requests for 1300 cards, never 1300).
     *
     * Returns only the rows that EXIST: a result shorter than [oracleIds] means those ids have no
     * row in the table yet, never "the server stopped early" — callers must treat a missing id as
     * [com.mmg.manahub.core.domain.repository.CardStrategyTagsResult.NotFound] and keep going,
     * never abort the remaining chunks. Order is not guaranteed; callers key off
     * [CardStrategyTagsRowDto.oracleId].
     *
     * Throws on a genuine transport/decode failure (callers are responsible for catching).
     * Implementations MUST reject a chunk larger than [CARD_STRATEGY_TAGS_MAX_IDS_PER_REQUEST]
     * rather than issue a request PostgREST could silently truncate.
     */
    suspend fun getByOracleIds(oracleIds: List<String>): List<CardStrategyTagsRowDto>

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

    override suspend fun getByOracleIds(oracleIds: List<String>): List<CardStrategyTagsRowDto> {
        val ids = oracleIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return emptyList()
        require(ids.size <= CARD_STRATEGY_TAGS_MAX_IDS_PER_REQUEST) {
            "card_strategy_tags batch of ${ids.size} ids exceeds the $CARD_STRATEGY_TAGS_MAX_IDS_PER_REQUEST cap"
        }
        return withContext(dispatcherProvider.io) {
            supabaseClient.postgrest["card_strategy_tags"]
                .select { filter { isIn("oracle_id", ids) } }
                .decodeList<CardStrategyTagsRowDto>()
        }
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
