package com.mmg.manahub.core.gamification.data.remote

import com.mmg.manahub.core.di.IoDispatcher
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of [GamificationRemoteDataSource] (ADR-002 §11).
 *
 * Mirrors [com.mmg.manahub.core.data.remote.collection.SupabaseCollectionDataSource] exactly:
 * - `rpc("name", buildJsonObject { put(...) })` — params are a [kotlinx.serialization.json.JsonObject].
 * - Batch upserts serialize `rows` via `json.encodeToJsonElement(rows)` → `put("p_rows", jsonArray)`.
 * - `rpc().decodeList<T>()` for pulls (never `.body`); all timestamps are Long epoch-millis.
 * - Every call is wrapped in [runCatching] on [IoDispatcher] and returns a [Result].
 *
 * The user is resolved server-side via `auth.uid()`; no user_id is sent in any payload.
 */
@Singleton
class SupabaseGamificationDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
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

    override suspend fun getXpChangesSince(since: Long): Result<List<XpTransactionChangeDto>> =
        withContext(ioDispatcher) {
            runCatching {
                val params = buildJsonObject { put("p_since", since) }
                supabaseClient.postgrest
                    .rpc("get_xp_transactions_changes_since", params)
                    .decodeList<XpTransactionChangeDto>()
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

    override suspend fun getAchievementChangesSince(since: Long): Result<List<AchievementProgressDto>> =
        withContext(ioDispatcher) {
            runCatching {
                val params = buildJsonObject { put("p_since", since) }
                supabaseClient.postgrest
                    .rpc("get_achievement_progress_changes_since", params)
                    .decodeList<AchievementProgressDto>()
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

    override suspend fun getEntitlementChangesSince(since: Long): Result<List<EntitlementDto>> =
        withContext(ioDispatcher) {
            runCatching {
                val params = buildJsonObject { put("p_since", since) }
                supabaseClient.postgrest
                    .rpc("get_entitlements_changes_since", params)
                    .decodeList<EntitlementDto>()
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

    override suspend fun getStreakChangesSince(since: Long): Result<List<StreakDto>> =
        withContext(ioDispatcher) {
            runCatching {
                val params = buildJsonObject { put("p_since", since) }
                supabaseClient.postgrest
                    .rpc("get_streaks_changes_since", params)
                    .decodeList<StreakDto>()
            }
        }

    override suspend fun getProgressionChangesSince(since: Long): Result<List<PlayerProgressionChangeDto>> =
        withContext(ioDispatcher) {
            runCatching {
                val params = buildJsonObject { put("p_since", since) }
                supabaseClient.postgrest
                    .rpc("get_progression_changes_since", params)
                    .decodeList<PlayerProgressionChangeDto>()
            }
        }
}
