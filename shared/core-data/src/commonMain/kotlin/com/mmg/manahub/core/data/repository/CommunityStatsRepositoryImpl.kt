package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.cache.CommunityAggregateCache
import com.mmg.manahub.core.data.remote.CommunityStatsRemoteDataSource
import com.mmg.manahub.core.data.remote.dto.CommunityStatsRpcDto
import com.mmg.manahub.core.domain.repository.CommunityStatsRepository
import com.mmg.manahub.core.model.CommunityEntry
import com.mmg.manahub.core.model.CommunityMilestone
import com.mmg.manahub.core.model.CommunityStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

private val communityStatsJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** Cache-first Supabase-backed [CommunityStatsRepository] (Home feature overhaul Phase 1.2.b). */
private const val COMMUNITY_STATS_CACHE_KEY = "home_community_stats"

/** 12-hour freshness window, per the plan's 6-12h TTL guidance. */
private const val COMMUNITY_STATS_FRESH_MS = 12L * 60 * 60 * 1000

/**
 * Real, cache-first implementation of [CommunityStatsRepository] backed by the Supabase RPC
 * `get_community_stats()` (Home feature overhaul Phase 1.2.b — replaces the old
 * `CommunityStatsRepositoryStub`, which always emitted null).
 *
 * Reuses the existing [CommunityAggregateCache] key-value abstraction (Room on Android,
 * IndexedDB/no-op on web) rather than a new table — the raw RPC JSON is cached under
 * [COMMUNITY_STATS_CACHE_KEY] with a 12h TTL. [observeCommunityStats] emits the cached value
 * immediately (or null on a cold start with no cache), then refreshes lazily on first
 * subscription when the cache is missing or stale — never at app startup. A failed refresh
 * silently falls back to the cached value (or null); it never surfaces as an inline error.
 */
class CommunityStatsRepositoryImpl(
    private val remote: CommunityStatsRemoteDataSource,
    private val cache: CommunityAggregateCache,
    private val crashReporter: CrashReporter,
    private val now: () -> Long,
) : CommunityStatsRepository {

    override fun observeCommunityStats(): Flow<CommunityStats?> = flow {
        val cached = cache.get(COMMUNITY_STATS_CACHE_KEY)
        val cachedStats = cached?.let { decode(it.json) }
        emit(cachedStats)

        val isStale = cached == null || (now() - cached.cachedAt) > COMMUNITY_STATS_FRESH_MS
        if (!isStale) return@flow

        val fresh = remote.getCommunityStats().getOrNull()
        if (fresh != null) {
            runCatching {
                cache.insert(
                    key = COMMUNITY_STATS_CACHE_KEY,
                    json = communityStatsJson.encodeToString(CommunityStatsRpcDto.serializer(), fresh),
                    cachedAt = now(),
                )
            }
            emit(fresh.toDomain())
        } else if (cachedStats == null) {
            crashReporter.log("home_community_stats_fetch_failed")
        }
    }.catch {
        crashReporter.log("home_community_stats_flow_error")
        emit(null)
    }

    private fun decode(json: String): CommunityStats? =
        runCatching {
            communityStatsJson.decodeFromString(CommunityStatsRpcDto.serializer(), json).toDomain()
        }.getOrNull()

    private fun CommunityStatsRpcDto.toDomain(): CommunityStats = CommunityStats(
        // No commander/deck-archetype data is synced server-side (Home feature overhaul decision
        // 1.2.b) — these stay permanently empty rather than being backed by fabricated data.
        topCommanders = emptyList(),
        metaArchetypes = emptyList(),
        mostWishlisted = mostWishlisted.map { row ->
            CommunityEntry(
                id = row.cardId,
                // Server-side name is always null (unpopulated `cards` catalog) — resolved
                // client-side by the ViewModel via the local card cache; left blank here.
                name = row.name.orEmpty(),
                count = row.count,
                percentage = 0.0,
            )
        },
        milestones = milestones.map { row -> CommunityMilestone(id = row.id, label = row.label, value = row.value) },
    )
}
