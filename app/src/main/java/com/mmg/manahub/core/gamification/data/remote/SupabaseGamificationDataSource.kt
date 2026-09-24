package com.mmg.manahub.core.gamification.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * Supabase implementation of [GamificationRemoteDataSource] (ADR-002 §11).
 *
 * Mirrors [com.mmg.manahub.core.data.remote.collection.SupabaseCollectionDataSource] exactly:
 * - `rpc("name", buildJsonObject { put(...) })` — params are a [kotlinx.serialization.json.JsonObject].
 * - Batch upserts serialize `rows` via `json.encodeToJsonElement(rows)` → `put("p_rows", jsonArray)`.
 * - `rpc().decodeList<T>()` for the keyset-paged `get_*_page` pulls (never `.body`); all timestamps
 *   are Long epoch-millis.
 * - Every call is wrapped in [runCatching] on [IoDispatcher] and returns a [Result].
 *
 * The user is resolved server-side via `auth.uid()`; no user_id is sent in any payload.
 */
class SupabaseGamificationDataSource(
    private val supabaseClient: SupabaseClient,
    private val ioDispatcher: CoroutineDispatcher,
) : GamificationRemoteDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun pushXpTransactions(rows: List<XpTransactionUploadDto>): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val jsonArray = json.encodeToJsonElement(rows)
                val params = buildJsonObject { put("p_rows", jsonArray) }
                supabaseClient.postgrest.rpc("batch_upsert_xp_transactions", params)
                Unit
            }
        }

    override suspend fun getXpTransactionsPage(
        afterSeq: Long,
        limit: Int,
    ): Result<List<XpTransactionPageDto>> = withContext(ioDispatcher) {
        runCatching {
            val params = buildJsonObject {
                put("p_after_seq", afterSeq)
                put("p_limit", limit)
            }
            supabaseClient.postgrest
                .rpc("get_xp_transactions_page", params)
                .decodeList<XpTransactionPageDto>()
        }
    }

    override suspend fun mergeAchievements(rows: List<AchievementProgressDto>): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val jsonArray = json.encodeToJsonElement(rows)
                val params = buildJsonObject { put("p_rows", jsonArray) }
                supabaseClient.postgrest.rpc("merge_achievement_progress", params)
                Unit
            }
        }

    override suspend fun getAchievementProgressPage(
        afterChangedAt: Long?,
        afterAchievementId: String?,
        limit: Int,
    ): Result<List<AchievementProgressPageDto>> = withContext(ioDispatcher) {
        runCatching {
            supabaseClient.postgrest
                .rpc("get_achievement_progress_page", keysetParams(afterChangedAt, "p_after_achievement_id", afterAchievementId, limit))
                .decodeList<AchievementProgressPageDto>()
        }
    }

    override suspend fun mergeEntitlements(rows: List<EntitlementDto>): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val jsonArray = json.encodeToJsonElement(rows)
                val params = buildJsonObject { put("p_rows", jsonArray) }
                supabaseClient.postgrest.rpc("merge_entitlements", params)
                Unit
            }
        }

    override suspend fun getEntitlementsPage(
        afterChangedAt: Long?,
        afterUnlockableId: String?,
        limit: Int,
    ): Result<List<EntitlementPageDto>> = withContext(ioDispatcher) {
        runCatching {
            supabaseClient.postgrest
                .rpc("get_entitlements_page", keysetParams(afterChangedAt, "p_after_unlockable_id", afterUnlockableId, limit))
                .decodeList<EntitlementPageDto>()
        }
    }

    override suspend fun mergeStreaks(rows: List<StreakDto>): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val jsonArray = json.encodeToJsonElement(rows)
                val params = buildJsonObject { put("p_rows", jsonArray) }
                supabaseClient.postgrest.rpc("merge_streaks", params)
                Unit
            }
        }

    override suspend fun getStreaksPage(
        afterChangedAt: Long?,
        afterType: String?,
        limit: Int,
    ): Result<List<StreakPageDto>> = withContext(ioDispatcher) {
        runCatching {
            supabaseClient.postgrest
                .rpc("get_streaks_page", keysetParams(afterChangedAt, "p_after_type", afterType, limit))
                .decodeList<StreakPageDto>()
        }
    }

    // The RPC rejects a half cursor (22023), so both fields are sent together or not at all.
    private fun keysetParams(afterChangedAt: Long?, keyParam: String, afterKey: String?, limit: Int) =
        buildJsonObject {
            put("p_since", 0L)
            if (afterChangedAt != null && afterKey != null) {
                put("p_after_changed_at", afterChangedAt)
                put(keyParam, afterKey)
            }
            put("p_limit", limit)
        }
}
