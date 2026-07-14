package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.cache.CachedAggregateEntry
import com.mmg.manahub.core.data.cache.CommunityAggregateCache
import com.mmg.manahub.core.data.remote.CommunityStatsRemoteDataSource
import com.mmg.manahub.core.data.remote.dto.CommunityStatsRpcDto
import com.mmg.manahub.core.data.remote.dto.MilestoneRowDto
import com.mmg.manahub.core.data.remote.dto.MostWishlistedRowDto
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [CommunityStatsRepositoryImpl] (Home feature overhaul Phase 1.2.b).
 *
 * TESTABILITY NOTE: [CommunityStatsRemoteDataSource] is a concrete (non-open, non-interface)
 * class that hard-wraps a real Supabase [io.github.jan.supabase.SupabaseClient] — there is no
 * seam to inject a fake/mock HTTP engine for it the way [ArchidektTrendingRepositoryImplTest]
 * does for `ArchidektClient` (which takes a plain Ktor `HttpClient`). `commonTest` for this module
 * only has `kotlin-test` + `coroutines-test` (no MockK, which is JVM-only and unavailable on the
 * wasmJs target), and `CommunityStatsRemoteDataSource` is `final`, so it cannot be subclassed
 * either. These tests therefore exercise every path that does NOT require a successful RPC call —
 * which, thanks to the cache-first design, is most of the interesting behavior (cache-hit
 * short-circuits the network entirely, decode/catch isolation) — using a real (but never actually
 * network-called) `SupabaseClient` purely to satisfy the constructor. The "cold start, cache
 * empty/stale -> live RPC succeeds" path is NOT covered here for that reason.
 *
 * Recommendation (not applied — routes through android-kotlin-architect per CLAUDE.md's agent
 * learning protocol): extract a small `interface CommunityStatsApi { suspend fun
 * getCommunityStats(): Result<CommunityStatsRpcDto> }` that `CommunityStatsRemoteDataSource`
 * implements, and have [CommunityStatsRepositoryImpl] depend on the interface — this closes the
 * gap and lets the remaining "stale cache -> live fetch" branch be tested with a trivial fake.
 */
class CommunityStatsRepositoryImplTest {

    private val dtoJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private class FakeAggregateCache(initial: CachedAggregateEntry? = null) : CommunityAggregateCache {
        var stored: CachedAggregateEntry? = initial
        var getCallCount = 0
        var shouldThrowOnGet = false
        override suspend fun get(key: String): CachedAggregateEntry? {
            getCallCount++
            if (shouldThrowOnGet) throw RuntimeException("cache read failed")
            return stored
        }
        override suspend fun insert(key: String, json: String, cachedAt: Long) {
            stored = CachedAggregateEntry(key, json, cachedAt)
        }
    }

    private class FakeCrashReporter : CrashReporter {
        val logs = mutableListOf<String>()
        override fun recordException(throwable: Throwable) {}
        override fun log(message: String) { logs.add(message) }
        override fun setCustomKey(key: String, value: String) {}
    }

    /** A [CommunityStatsRemoteDataSource] wired to a real (never network-called) SupabaseClient. */
    private fun unusedRemote(): CommunityStatsRemoteDataSource {
        val client = createSupabaseClient(
            supabaseUrl = "https://unit-test.invalid.supabase.co",
            supabaseKey = "unit-test-anon-key",
        ) { install(Postgrest) }
        return CommunityStatsRemoteDataSource(client, DispatcherProvider())
    }

    private fun repo(
        cache: CommunityAggregateCache,
        crashReporter: CrashReporter = FakeCrashReporter(),
        now: () -> Long = { 1_000_000L },
    ) = CommunityStatsRepositoryImpl(
        remote = unusedRemote(), cache = cache, crashReporter = crashReporter, now = now,
    )

    private fun rpcJson(
        mostWishlisted: List<MostWishlistedRowDto> = emptyList(),
        milestones: List<MilestoneRowDto> = emptyList(),
    ): String = dtoJson.encodeToString(
        CommunityStatsRpcDto.serializer(),
        CommunityStatsRpcDto(mostWishlisted = mostWishlisted, milestones = milestones),
    )

    // ── Cache-first emission ───────────────────────────────────────────────────

    @Test
    fun `fresh cache emits mapped stats without any network call`() = runTest {
        val json = rpcJson(
            mostWishlisted = listOf(MostWishlistedRowDto(cardId = "c1", name = null, count = 5)),
            milestones = listOf(MilestoneRowDto(id = "active_collectors", label = "Active collectors", value = "4")),
        )
        val now = 10_000_000L
        val cache = FakeAggregateCache(CachedAggregateEntry("home_community_stats", json, cachedAt = now - 1_000L))

        val emissions = repo(cache = cache, now = { now }).observeCommunityStats().toList()

        assertEquals(1, emissions.size)
        val stats = emissions.first()
        assertEquals(1, stats!!.mostWishlisted.size)
        assertEquals("c1", stats.mostWishlisted.first().id)
        assertEquals(1, stats.milestones.size)
        assertEquals("active_collectors", stats.milestones.first().id)
        assertEquals("4", stats.milestones.first().value)
    }

    @Test
    fun `null server-side name maps to an empty string, never a crash (server catalog is unpopulated)`() =
        runTest {
            val json = rpcJson(mostWishlisted = listOf(MostWishlistedRowDto(cardId = "c1", name = null, count = 3)))
            val now = 10_000_000L
            val cache = FakeAggregateCache(CachedAggregateEntry("home_community_stats", json, cachedAt = now - 1_000L))

            val stats = repo(cache = cache, now = { now }).observeCommunityStats().toList().first()

            assertEquals("", stats!!.mostWishlisted.first().name)
            assertEquals(3, stats.mostWishlisted.first().count)
        }

    @Test
    fun `topCommanders and metaArchetypes are always empty (no server-side commander data)`() = runTest {
        val json = rpcJson(mostWishlisted = listOf(MostWishlistedRowDto(cardId = "c1", name = "X", count = 1)))
        val now = 10_000_000L
        val cache = FakeAggregateCache(CachedAggregateEntry("home_community_stats", json, cachedAt = now - 1_000L))

        val stats = repo(cache = cache, now = { now }).observeCommunityStats().toList().first()

        assertTrue(stats!!.topCommanders.isEmpty())
        assertTrue(stats.metaArchetypes.isEmpty())
    }

    @Test
    fun `milestone value is passed through as a pre-formatted string`() = runTest {
        val json = rpcJson(
            milestones = listOf(
                MilestoneRowDto(id = "active_collectors", label = "Active collectors", value = "4"),
                MilestoneRowDto(id = "decks_built", label = "Decks built", value = "29"),
            ),
        )
        val now = 10_000_000L
        val cache = FakeAggregateCache(CachedAggregateEntry("home_community_stats", json, cachedAt = now - 1_000L))

        val stats = repo(cache = cache, now = { now }).observeCommunityStats().toList().first()

        assertEquals(listOf("active_collectors", "decks_built"), stats!!.milestones.map { it.id })
        assertEquals(listOf("4", "29"), stats.milestones.map { it.value })
    }

    // ── Error / catch isolation ────────────────────────────────────────────────

    @Test
    fun `corrupted but fresh cached JSON degrades to null without crashing`() = runTest {
        val now = 10_000_000L
        val cache = FakeAggregateCache(CachedAggregateEntry("home_community_stats", "{ not valid json", cachedAt = now - 1))

        val stats = repo(cache = cache, now = { now }).observeCommunityStats().toList().first()

        assertNull(stats)
    }

    @Test
    fun `cache throwing on read degrades to a single null emission (outer catch isolation)`() = runTest {
        val cache = FakeAggregateCache().apply { shouldThrowOnGet = true }
        val crashReporter = FakeCrashReporter()

        val emissions = repo(cache = cache, crashReporter = crashReporter).observeCommunityStats().toList()

        assertEquals(listOf(null), emissions)
        assertTrue(crashReporter.logs.isNotEmpty())
    }

    @Test
    fun `empty mostWishlisted list maps to an empty list, not an error`() = runTest {
        val json = rpcJson(mostWishlisted = emptyList())
        val now = 10_000_000L
        val cache = FakeAggregateCache(CachedAggregateEntry("home_community_stats", json, cachedAt = now - 1))

        val stats = repo(cache = cache, now = { now }).observeCommunityStats().toList().first()

        assertTrue(stats!!.mostWishlisted.isEmpty())
    }
}
