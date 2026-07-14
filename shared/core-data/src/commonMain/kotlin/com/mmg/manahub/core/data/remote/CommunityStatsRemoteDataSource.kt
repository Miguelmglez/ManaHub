package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.remote.dto.CommunityStatsRpcDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject

/**
 * Remote data source for the Home dashboard's community-stats RPC (Home feature overhaul
 * Phase 1.2.b). Calls the parameterless, `SECURITY DEFINER`, `authenticated`-only Supabase RPC
 * `get_community_stats()`, which reads from hourly-refreshed materialized views — never raw
 * per-request table scans from the client.
 */
class CommunityStatsRemoteDataSource(
    private val supabaseClient: SupabaseClient,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider(),
) {
    suspend fun getCommunityStats(): Result<CommunityStatsRpcDto> =
        withContext(dispatcherProvider.io) {
            runCatching {
                supabaseClient.postgrest
                    .rpc("get_community_stats", buildJsonObject {})
                    .decodeSingle<CommunityStatsRpcDto>()
            }
        }
}
