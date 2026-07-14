package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.cache.CachedAggregateEntry
import com.mmg.manahub.core.data.cache.CommunityAggregateCache
import com.mmg.manahub.core.data.remote.CommunityAggregateApiContract
import com.mmg.manahub.core.data.remote.SixtyFallbackFetcher
import com.mmg.manahub.core.data.remote.dto.AggregateCardEntryDto
import com.mmg.manahub.core.data.remote.dto.AvgTypeDistributionDto
import com.mmg.manahub.core.data.remote.dto.BuildProgressDto
import com.mmg.manahub.core.data.remote.dto.CommanderAggregateResponseDto
import com.mmg.manahub.core.data.remote.dto.SimilarDecksResponseDto
import com.mmg.manahub.core.data.remote.dto.SixtyAggregateResponseDto
import com.mmg.manahub.core.data.remote.dto.TrendingResponseDto
import com.mmg.manahub.core.model.AggregateSource
import com.mmg.manahub.core.model.CommunityAggregate
import com.mmg.manahub.core.model.DataResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Repository-layering coverage for [CommunityAggregateRepositoryImpl] (Phase 3.3): fresh
 * cache / stale-with-worker-recovery / miss-hits-worker / worker-down-with-stale-cache /
 * worker-down-with-fallback / building-in-progress / feature-flag-off. Uses hand-written
 * fakes of the [CommunityAggregateApiContract] and [CommunityAggregateCache] SEAMS (not a
 * Ktor engine mock — `ktor-client-mock` is not in this project's dependency catalog yet and
 * adding it is `.gradle.kts`-owned by `android-kotlin-architect`; these interfaces achieve
 * the same "no real network calls in unit tests" goal). `commonMain`/`commonTest` — no
 * Android dependency, runs on every KMP target.
 */
class CommunityAggregateRepositoryImplTest {

    private val noopCrashReporter = object : CrashReporter {
        override fun recordException(throwable: Throwable) = Unit
        override fun log(message: String) = Unit
        override fun setCustomKey(key: String, value: String) = Unit
    }

    private val dispatcherProvider = DispatcherProvider()

    private class FakeCache : CommunityAggregateCache {
        val store = mutableMapOf<String, CachedAggregateEntry>()
        override suspend fun get(key: String): CachedAggregateEntry? = store[key]
        override suspend fun insert(key: String, json: String, cachedAt: Long) {
            store[key] = CachedAggregateEntry(key, json, cachedAt)
        }
    }

    private class FakeApi(
        var commanderResult: (() -> CommanderAggregateResponseDto)? = null,
        var sixtyResult: (() -> SixtyAggregateResponseDto)? = null,
        var similarResult: (() -> SimilarDecksResponseDto)? = null,
        var trendingResult: (() -> TrendingResponseDto)? = null,
    ) : CommunityAggregateApiContract {
        var commanderCallCount = 0
        var sixtyCallCount = 0

        override suspend fun getCommanderAggregate(commanderName: String): CommanderAggregateResponseDto {
            commanderCallCount++
            return commanderResult?.invoke() ?: throw IllegalStateException("Worker down")
        }

        override suspend fun getSixtyAggregate(signatureCards: List<String>, format: Int): SixtyAggregateResponseDto {
            sixtyCallCount++
            return sixtyResult?.invoke() ?: throw IllegalStateException("Worker down")
        }

        override suspend fun getSimilar(commanderName: String, limit: Int): SimilarDecksResponseDto =
            similarResult?.invoke() ?: throw IllegalStateException("Worker down")

        override suspend fun getTrending(week: String?): TrendingResponseDto =
            trendingResult?.invoke() ?: throw IllegalStateException("Worker down")
    }

    private val defaultCommanderDto = CommanderAggregateResponseDto(
        status = "materialized",
        commander = "Atraxa, Praetors' Voice",
        numDecksSampled = 100,
        avgTypeDistribution = AvgTypeDistributionDto(land = 35),
        cards = listOf(AggregateCardEntryDto("Sol Ring", "id", 0.9f, -0.1f, "gamechangers", 90)),
        cachedAt = 1000L,
    )

    private val defaultSixtyMaterializedDto = SixtyAggregateResponseDto(
        status = "materialized",
        canonicalKey = "krenko-mob-boss",
        format = 3,
        numDecksSampled = 20,
        cards = listOf(AggregateCardEntryDto("Krenko, Mob Boss", null, 1f, 0.5f, "mainboard", 20)),
        cachedAt = 1000L,
    )

    private fun repository(
        api: CommunityAggregateApiContract,
        cache: CommunityAggregateCache = FakeCache(),
        fallback: SixtyFallbackFetcher = SixtyFallbackFetcher { _, _ -> null },
        clock: () -> Long = { 2_000_000L },
        engineEnabled: suspend () -> Boolean = { true },
    ): CommunityAggregateRepositoryImpl = CommunityAggregateRepositoryImpl(
        api = api,
        cache = cache,
        sixtyFallback = fallback,
        crashReporter = noopCrashReporter,
        dispatcherProvider = dispatcherProvider,
        now = clock,
        isEngineEnabled = engineEnabled,
    )

    // ---- Commander path ----------------------------------------------------------------

    @Test
    fun `commander - cache miss fetches from Worker and caches the result`() = runTest {
        val api = FakeApi(commanderResult = { defaultCommanderDto })
        val cache = FakeCache()
        val repo = repository(api, cache)

        val result = repo.getCommanderAggregate("Atraxa, Praetors' Voice")

        val success = assertIs<DataResult.Success<CommunityAggregate.Commander>>(result)
        assertEquals(AggregateSource.WORKER, success.data.source)
        assertEquals(1, api.commanderCallCount)
        assertTrue(cache.store.containsKey("agg:commander:atraxa-praetors-voice"))
    }

    @Test
    fun `commander - fresh cache short-circuits the Worker call`() = runTest {
        val api = FakeApi(commanderResult = { defaultCommanderDto })
        val cache = FakeCache()
        val now = 5_000_000L
        cache.store["agg:commander:atraxa-praetors-voice"] = CachedAggregateEntry(
            "agg:commander:atraxa-praetors-voice",
            kotlinx.serialization.json.Json.encodeToString(CommanderAggregateResponseDto.serializer(), defaultCommanderDto),
            now - 1000, // 1s old, well within the 7-day freshness window
        )
        val repo = repository(api, cache, clock = { now })

        val result = repo.getCommanderAggregate("Atraxa, Praetors' Voice")

        val success = assertIs<DataResult.Success<CommunityAggregate.Commander>>(result)
        assertEquals(AggregateSource.CACHE, success.data.source)
        assertEquals(0, api.commanderCallCount)
    }

    @Test
    fun `commander - worker down with a stale cache serves the stale entry flagged isStale`() = runTest {
        val api = FakeApi() // throws — Worker down
        val cache = FakeCache()
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
        val now = 20_000_000L
        cache.store["agg:commander:atraxa-praetors-voice"] = CachedAggregateEntry(
            "agg:commander:atraxa-praetors-voice",
            kotlinx.serialization.json.Json.encodeToString(CommanderAggregateResponseDto.serializer(), defaultCommanderDto),
            now - sevenDaysMs - 1000, // past the freshness window
        )
        val repo = repository(api, cache, clock = { now })

        val result = repo.getCommanderAggregate("Atraxa, Praetors' Voice")

        val success = assertIs<DataResult.Success<CommunityAggregate.Commander>>(result)
        assertTrue(success.isStale)
        assertEquals(AggregateSource.CACHE, success.data.source)
    }

    @Test
    fun `commander - worker down with no cache at all returns an Error, never a synthetic snapshot`() = runTest {
        val api = FakeApi()
        val repo = repository(api, FakeCache())

        val result = repo.getCommanderAggregate("Atraxa, Praetors' Voice")

        assertIs<DataResult.Error>(result)
    }

    // ---- 60-card path --------------------------------------------------------------------

    @Test
    fun `sixty - worker reports building status, is not cached`() = runTest {
        val buildingDto = SixtyAggregateResponseDto(
            status = "building",
            canonicalKey = "krenko-mob-boss",
            progress = BuildProgressDto(collected = 5, target = 20),
        )
        val api = FakeApi(sixtyResult = { buildingDto })
        val cache = FakeCache()
        val repo = repository(api, cache)

        val result = repo.getSixtyAggregate(listOf("Krenko, Mob Boss"), 3)

        val success = assertIs<DataResult.Success<CommunityAggregate.Sixty>>(result)
        val building = assertIs<CommunityAggregate.Sixty.Building>(success.data)
        assertEquals(5, building.collected)
        assertTrue(cache.store.isEmpty(), "a 'building' response must never be cached")
    }

    @Test
    fun `sixty - materialized worker response is cached and returned`() = runTest {
        val api = FakeApi(sixtyResult = { defaultSixtyMaterializedDto })
        val cache = FakeCache()
        val repo = repository(api, cache)

        val result = repo.getSixtyAggregate(listOf("Krenko, Mob Boss"), 3)

        val success = assertIs<DataResult.Success<CommunityAggregate.Sixty>>(result)
        assertIs<CommunityAggregate.Sixty.Materialized>(success.data)
        assertTrue(cache.store.containsKey("agg:3:krenko-mob-boss"))
    }

    @Test
    fun `sixty - worker down falls back to the direct-Archidekt fetcher when no cache exists`() = runTest {
        val api = FakeApi()
        var fallbackCalled = false
        val fallback = SixtyFallbackFetcher { card, format ->
            fallbackCalled = true
            CommunityAggregate.Sixty.Materialized(
                canonicalKey = "krenko-mob-boss",
                format = format,
                numDecksSampled = 3,
                deckSummaries = emptyList(),
                colorProfile = emptyMap(),
                cards = emptyList(),
                cachedAt = 1L,
                source = AggregateSource.DIRECT_FALLBACK,
            )
        }
        val repo = repository(api, FakeCache(), fallback = fallback)

        val result = repo.getSixtyAggregate(listOf("Krenko, Mob Boss"), 3)

        assertTrue(fallbackCalled)
        val success = assertIs<DataResult.Success<CommunityAggregate.Sixty>>(result)
        val materialized = assertIs<CommunityAggregate.Sixty.Materialized>(success.data)
        assertEquals(AggregateSource.DIRECT_FALLBACK, materialized.source)
    }

    @Test
    fun `sixty - worker down, no cache, fallback also fails yields Error (degraded, never a crash)`() = runTest {
        val api = FakeApi()
        val fallback = SixtyFallbackFetcher { _, _ -> null }
        val repo = repository(api, FakeCache(), fallback = fallback)

        val result = repo.getSixtyAggregate(listOf("Krenko, Mob Boss"), 3)

        assertIs<DataResult.Error>(result)
    }

    @Test
    fun `sixty - stale cache is preferred over the direct fallback`() = runTest {
        val api = FakeApi()
        var fallbackCalled = false
        val fallback = SixtyFallbackFetcher { _, _ -> fallbackCalled = true; null }
        val cache = FakeCache()
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
        val now = 20_000_000L
        cache.store["agg:3:krenko-mob-boss"] = CachedAggregateEntry(
            "agg:3:krenko-mob-boss",
            kotlinx.serialization.json.Json.encodeToString(SixtyAggregateResponseDto.serializer(), defaultSixtyMaterializedDto),
            now - sevenDaysMs - 1000,
        )
        val repo = repository(api, cache, fallback = fallback, clock = { now })

        val result = repo.getSixtyAggregate(listOf("Krenko, Mob Boss"), 3)

        assertTrue(!fallbackCalled, "a stale cache hit must short-circuit the fallback")
        val success = assertIs<DataResult.Success<CommunityAggregate.Sixty>>(result)
        assertTrue(success.isStale)
    }

    @Test
    fun `sixty - empty signature card list is rejected before any network access`() = runTest {
        val api = FakeApi()
        val repo = repository(api, FakeCache())

        val result = repo.getSixtyAggregate(emptyList(), 3)

        assertIs<DataResult.Error>(result)
        assertEquals(0, api.sixtyCallCount)
    }

    // ---- Feature flag (D4) ----------------------------------------------------------------

    @Test
    fun `flag off - short-circuits every method with zero network or cache access`() = runTest {
        val api = FakeApi(commanderResult = { defaultCommanderDto }, sixtyResult = { defaultSixtyMaterializedDto })
        val cache = FakeCache()
        val repo = repository(api, cache, engineEnabled = { false })

        assertIs<DataResult.Error>(repo.getCommanderAggregate("Atraxa, Praetors' Voice"))
        assertIs<DataResult.Error>(repo.getSixtyAggregate(listOf("Krenko, Mob Boss"), 3))
        assertIs<DataResult.Error>(repo.getSimilarDecks("Atraxa, Praetors' Voice"))
        assertIs<DataResult.Error>(repo.getTrending(null))

        assertEquals(0, api.commanderCallCount)
        assertEquals(0, api.sixtyCallCount)
        assertTrue(cache.store.isEmpty())
    }

    // ---- Similar / trending (thin pass-throughs) -------------------------------------------

    @Test
    fun `similar - maps the Worker response to a plain name list`() = runTest {
        val api = FakeApi(similarResult = { SimilarDecksResponseDto(similar = listOf("Atraxa, Grand Unifier")) })
        val repo = repository(api)

        val result = repo.getSimilarDecks("Atraxa, Praetors' Voice", 5)

        val success = assertIs<DataResult.Success<List<String>>>(result)
        assertEquals(listOf("Atraxa, Grand Unifier"), success.data)
    }

    @Test
    fun `trending - worker failure returns an Error without throwing`() = runTest {
        val api = FakeApi()
        val repo = repository(api)

        assertIs<DataResult.Error>(repo.getTrending("2026-W28"))
    }
}
