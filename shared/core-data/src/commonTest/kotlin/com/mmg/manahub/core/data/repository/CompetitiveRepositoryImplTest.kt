package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.cache.CachedCompetitiveEntry
import com.mmg.manahub.core.data.cache.CompetitiveLimitedRatingsCache
import com.mmg.manahub.core.data.cache.CompetitiveMetaCache
import com.mmg.manahub.core.data.network.CompetitiveRequestQueue
import com.mmg.manahub.core.data.remote.CompetitiveApi
import com.mmg.manahub.core.data.remote.CompetitiveApiContract
import com.mmg.manahub.core.data.remote.dto.LimitedCardRatingDto
import com.mmg.manahub.core.data.remote.dto.LimitedRatingsSnapshotDto
import com.mmg.manahub.core.data.remote.dto.MetaSnapshotDto
import com.mmg.manahub.core.data.remote.dto.SourceStatusDto
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.LimitedRatingsSnapshot
import com.mmg.manahub.core.model.MetaSnapshot
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Repository-layering coverage for [CompetitiveRepositoryImpl] (Competitive feature, Phase 3):
 * fresh cache / stale-with-worker-recovery / miss-hits-worker / worker-down-with-stale-cache /
 * worker-down-with-no-cache / feature-flag-off, for both the weekly-meta and limited-ratings
 * paths. Uses hand-written fakes of the [CompetitiveApiContract]/cache SEAMS (same approach as
 * `CommunityAggregateRepositoryImplTest` -- no Ktor engine mock needed) plus a REAL
 * [CompetitiveRequestQueue] instance, mirroring `ScryfallRemoteDataSourceTest`'s precedent for
 * exercising the real rate-limit queue under `runTest`'s virtual clock (its `delay()` calls
 * resolve instantly, and the very first `execute()` call never actually delays since
 * `lastRequestTime` starts at `0L`). `commonMain`/`commonTest` -- no Android dependency, runs on
 * every KMP target.
 */
class CompetitiveRepositoryImplTest {

    private val noopCrashReporter = object : CrashReporter {
        override fun recordException(throwable: Throwable) = Unit
        override fun log(message: String) = Unit
        override fun setCustomKey(key: String, value: String) = Unit
    }

    /** Spy variant of [noopCrashReporter] -- records call counts so the benign-404 downgrade
     * (recordFailure's `benignHttpCodes` param) can be asserted without a mocking framework
     * (`commonTest` stays MockK-free). */
    private class SpyCrashReporter : CrashReporter {
        var recordExceptionCount = 0
        var logCount = 0
        override fun recordException(throwable: Throwable) { recordExceptionCount++ }
        override fun log(message: String) { logCount++ }
        override fun setCustomKey(key: String, value: String) = Unit
    }

    private val dispatcherProvider = DispatcherProvider()

    private val dtoJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private class FakeMetaCache : CompetitiveMetaCache {
        val store = mutableMapOf<String, CachedCompetitiveEntry>()
        override suspend fun get(key: String): CachedCompetitiveEntry? = store[key]
        override suspend fun insert(key: String, json: String, cachedAt: Long) {
            store[key] = CachedCompetitiveEntry(key, json, cachedAt)
        }
    }

    private class FakeLimitedRatingsCache : CompetitiveLimitedRatingsCache {
        val store = mutableMapOf<String, CachedCompetitiveEntry>()
        override suspend fun get(key: String): CachedCompetitiveEntry? = store[key]
        override suspend fun insert(key: String, json: String, cachedAt: Long) {
            store[key] = CachedCompetitiveEntry(key, json, cachedAt)
        }
    }

    private class FakeApi(
        var metaResult: (() -> MetaSnapshotDto)? = null,
        var limitedResult: (() -> LimitedRatingsSnapshotDto)? = null,
    ) : CompetitiveApiContract {
        var metaCallCount = 0
        var limitedCallCount = 0

        override suspend fun getWeeklyMeta(format: String): MetaSnapshotDto {
            metaCallCount++
            return metaResult?.invoke() ?: throw IllegalStateException("Worker down")
        }

        override suspend fun getLimitedRatings(setCode: String, format: String): LimitedRatingsSnapshotDto {
            limitedCallCount++
            return limitedResult?.invoke() ?: throw IllegalStateException("Worker down")
        }
    }

    private val defaultMetaDto = MetaSnapshotDto(
        format = "modern",
        week = "2026-W32",
        numDecksSampled = 100,
        archetypes = emptyList(),
        sources = listOf(SourceStatusDto("mtgo", false, "DAYBREAK_SERVICE_ID not configured")),
        cachedAt = 1000L,
    )

    private val defaultLimitedDto = LimitedRatingsSnapshotDto(
        expansion = "BLB",
        format = "PremierDraft",
        cards = listOf(LimitedCardRatingDto(name = "Sample Card", color = "G", rarity = "rare", sampleSize = 100)),
        cachedAt = 1000L,
    )

    private fun repository(
        api: CompetitiveApiContract,
        metaCache: CompetitiveMetaCache = FakeMetaCache(),
        limitedCache: CompetitiveLimitedRatingsCache = FakeLimitedRatingsCache(),
        clock: () -> Long = { 2_000_000L },
        engineEnabled: suspend () -> Boolean = { true },
        crashReporter: CrashReporter = noopCrashReporter,
    ): CompetitiveRepositoryImpl = CompetitiveRepositoryImpl(
        api = api,
        requestQueue = CompetitiveRequestQueue(),
        metaCache = metaCache,
        limitedRatingsCache = limitedCache,
        crashReporter = crashReporter,
        dispatcherProvider = dispatcherProvider,
        now = clock,
        isEngineEnabled = engineEnabled,
    )

    /** Builds a real Ktor-backed [CompetitiveApiContract] whose engine always returns [status]
     * for any request -- used ONLY for the benign-404-downgrade tests below, which need a real
     * [io.ktor.client.plugins.ResponseException] (the hand-written [FakeApi] above throws a
     * plain [IllegalStateException], which `recordFailure` can't classify by status code). */
    private fun mockEngineApi(status: HttpStatusCode): CompetitiveApiContract {
        val engine = MockEngine { respondError(status) }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(dtoJson) }
            // CompetitiveApi's KDoc documents that its injected client is expected to be
            // configured with expectSuccess = true (the codebase-wide convention) -- without it,
            // Ktor doesn't throw ResponseException on a non-2xx status, so recordFailure's
            // benignHttpCodes classification (which relies on `e as? ResponseException`) would
            // never trigger and this test would silently prove nothing.
            expectSuccess = true
        }
        return CompetitiveApi(httpClient, baseUrl = "https://competitive.manahub.workers.dev/")
    }

    // ---- Weekly meta path -----------------------------------------------------------------

    @Test
    fun `meta - cache miss fetches from Worker and caches the result`() = runTest {
        val api = FakeApi(metaResult = { defaultMetaDto })
        val cache = FakeMetaCache()
        val repo = repository(api, metaCache = cache)

        val result = repo.getWeeklyMeta("modern")

        val success = assertIs<DataResult.Success<MetaSnapshot>>(result)
        assertEquals("modern", success.data.format)
        assertEquals(1, api.metaCallCount)
        assertTrue(cache.store.containsKey("modern"))
    }

    @Test
    fun `meta - fresh cache short-circuits the Worker call`() = runTest {
        val api = FakeApi(metaResult = { defaultMetaDto })
        val cache = FakeMetaCache()
        val now = 5_000_000L
        cache.store["modern"] = CachedCompetitiveEntry(
            "modern",
            kotlinx.serialization.json.Json.encodeToString(MetaSnapshotDto.serializer(), defaultMetaDto),
            now - 1000, // 1s old, well within the 24h freshness window
        )
        val repo = repository(api, metaCache = cache, clock = { now })

        val result = repo.getWeeklyMeta("modern")

        val success = assertIs<DataResult.Success<MetaSnapshot>>(result)
        assertEquals(0, api.metaCallCount)
        assertEquals("modern", success.data.format)
    }

    @Test
    fun `meta - worker down with a stale cache serves the stale entry flagged isStale`() = runTest {
        val api = FakeApi() // throws — Worker down
        val cache = FakeMetaCache()
        val dayMs = 24L * 60 * 60 * 1000
        val now = 20_000_000L
        cache.store["modern"] = CachedCompetitiveEntry(
            "modern",
            kotlinx.serialization.json.Json.encodeToString(MetaSnapshotDto.serializer(), defaultMetaDto),
            now - dayMs - 1000, // past the freshness window
        )
        val repo = repository(api, metaCache = cache, clock = { now })

        val result = repo.getWeeklyMeta("modern")

        val success = assertIs<DataResult.Success<MetaSnapshot>>(result)
        assertTrue(success.isStale)
        assertEquals("modern", success.data.format)
    }

    @Test
    fun `meta - worker down with no cache at all returns an Error, never a synthetic snapshot`() = runTest {
        val api = FakeApi()
        val repo = repository(api, metaCache = FakeMetaCache())

        val result = repo.getWeeklyMeta("modern")

        assertIs<DataResult.Error>(result)
    }

    @Test
    fun `meta - empty archetypes list is a Success, never an Error`() = runTest {
        val api = FakeApi(metaResult = { defaultMetaDto.copy(archetypes = emptyList()) })
        val repo = repository(api)

        val result = repo.getWeeklyMeta("vintage")

        val success = assertIs<DataResult.Success<MetaSnapshot>>(result)
        assertTrue(success.data.archetypes.isEmpty())
        assertEquals(1, success.data.sources.size)
    }

    // ---- Limited ratings path --------------------------------------------------------------

    @Test
    fun `limited - cache miss fetches from Worker and caches the result`() = runTest {
        val api = FakeApi(limitedResult = { defaultLimitedDto })
        val cache = FakeLimitedRatingsCache()
        val repo = repository(api, limitedCache = cache)

        val result = repo.getLimitedRatings("BLB", "PremierDraft")

        val success = assertIs<DataResult.Success<LimitedRatingsSnapshot>>(result)
        assertEquals("BLB", success.data.expansion)
        assertEquals(1, api.limitedCallCount)
        assertTrue(cache.store.containsKey("BLB"))
    }

    @Test
    fun `limited - fresh cache short-circuits the Worker call`() = runTest {
        val api = FakeApi(limitedResult = { defaultLimitedDto })
        val cache = FakeLimitedRatingsCache()
        val now = 5_000_000L
        cache.store["BLB"] = CachedCompetitiveEntry(
            "BLB",
            kotlinx.serialization.json.Json.encodeToString(LimitedRatingsSnapshotDto.serializer(), defaultLimitedDto),
            now - 1000,
        )
        val repo = repository(api, limitedCache = cache, clock = { now })

        val result = repo.getLimitedRatings("BLB", "PremierDraft")

        val success = assertIs<DataResult.Success<LimitedRatingsSnapshot>>(result)
        assertEquals(0, api.limitedCallCount)
        assertEquals("BLB", success.data.expansion)
    }

    @Test
    fun `limited - worker down with a stale cache serves the stale entry flagged isStale`() = runTest {
        val api = FakeApi()
        val cache = FakeLimitedRatingsCache()
        val dayMs = 24L * 60 * 60 * 1000
        val now = 20_000_000L
        cache.store["BLB"] = CachedCompetitiveEntry(
            "BLB",
            kotlinx.serialization.json.Json.encodeToString(LimitedRatingsSnapshotDto.serializer(), defaultLimitedDto),
            now - dayMs - 1000,
        )
        val repo = repository(api, limitedCache = cache, clock = { now })

        val result = repo.getLimitedRatings("BLB", "PremierDraft")

        val success = assertIs<DataResult.Success<LimitedRatingsSnapshot>>(result)
        assertTrue(success.isStale)
    }

    @Test
    fun `limited - worker down with no cache at all returns an Error, never a synthetic snapshot`() = runTest {
        val api = FakeApi()
        val repo = repository(api, limitedCache = FakeLimitedRatingsCache())

        val result = repo.getLimitedRatings("BLB", "PremierDraft")

        assertIs<DataResult.Error>(result)
    }

    @Test
    fun `limited - cache key is the set code alone, independent of format`() = runTest {
        val api = FakeApi(limitedResult = { defaultLimitedDto })
        val cache = FakeLimitedRatingsCache()
        val repo = repository(api, limitedCache = cache)

        repo.getLimitedRatings("BLB", "TradDraft")

        assertTrue(cache.store.containsKey("BLB"))
        assertEquals(1, api.limitedCallCount)
    }

    @Test
    fun `limited - benign 404 downgrades to log-only, no non-fatal recorded`() = runTest {
        // limitedSetCode is a free-text field with zero validation -- a typo'd/unknown set code
        // is expected user input, not a bug, so recordFailure's benignHttpCodes downgrade must
        // suppress the non-fatal for a 404 specifically.
        val spy = SpyCrashReporter()
        val repo = repository(mockEngineApi(HttpStatusCode.NotFound), crashReporter = spy)

        val result = repo.getLimitedRatings("ZZZZZ", "PremierDraft")

        assertIs<DataResult.Error>(result)
        assertEquals(0, spy.recordExceptionCount)
        assertTrue(spy.logCount > 0) // still logged, just not recorded as a non-fatal
    }

    @Test
    fun `limited - non-404 failure still records a non-fatal (downgrade is 404-only)`() = runTest {
        val spy = SpyCrashReporter()
        val repo = repository(mockEngineApi(HttpStatusCode.InternalServerError), crashReporter = spy)

        val result = repo.getLimitedRatings("BLB", "PremierDraft")

        assertIs<DataResult.Error>(result)
        assertEquals(1, spy.recordExceptionCount)
    }

    @Test
    fun `meta - a 404 is NOT downgraded (format comes from a closed enum, a 404 there is a real bug)`() = runTest {
        val spy = SpyCrashReporter()
        val repo = repository(mockEngineApi(HttpStatusCode.NotFound), crashReporter = spy)

        val result = repo.getWeeklyMeta("modern")

        assertIs<DataResult.Error>(result)
        assertFalse(spy.recordExceptionCount == 0)
    }

    // ---- Feature flag -----------------------------------------------------------------------

    @Test
    fun `flag off - short-circuits every method with zero network or cache access`() = runTest {
        val api = FakeApi(metaResult = { defaultMetaDto }, limitedResult = { defaultLimitedDto })
        val metaCache = FakeMetaCache()
        val limitedCache = FakeLimitedRatingsCache()
        val repo = repository(api, metaCache = metaCache, limitedCache = limitedCache, engineEnabled = { false })

        assertIs<DataResult.Error>(repo.getWeeklyMeta("modern"))
        assertIs<DataResult.Error>(repo.getLimitedRatings("BLB", "PremierDraft"))

        assertEquals(0, api.metaCallCount)
        assertEquals(0, api.limitedCallCount)
        assertTrue(metaCache.store.isEmpty())
        assertTrue(limitedCache.store.isEmpty())
    }
}
