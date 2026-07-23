package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.cache.CachedCardStrategyTagsEntry
import com.mmg.manahub.core.data.cache.CardStrategyTagsCache
import com.mmg.manahub.core.data.remote.CardStrategyTagsRemoteDataSourceContract
import com.mmg.manahub.core.data.remote.dto.CardStrategyTagsPayloadDto
import com.mmg.manahub.core.data.remote.dto.CardStrategyTagsRowDto
import com.mmg.manahub.core.domain.repository.CardStrategyTagsResult
import com.mmg.manahub.core.domain.repository.CardStrategyTagsSubmission
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Repository-layering coverage for [CardStrategyTagsRepositoryImpl] (Deck Engine Unification
 * plan, D8, §5 Phase 5c): cache-hit-fresh / cache-miss-fetches-and-caches / stale-cache-remote-
 * recovers / remote-miss-falls-back-to-stale-cache / remote-down-with-no-cache / blank-oracleId.
 * Hand-written fakes of [CardStrategyTagsRemoteDataSourceContract] and [CardStrategyTagsCache]
 * (same seam-fake convention as `CommunityAggregateRepositoryImplTest` — no Ktor/Supabase client
 * mock needed). `commonMain`/`commonTest` — no Android dependency.
 */
class CardStrategyTagsRepositoryImplTest {

    private val noopCrashReporter = object : CrashReporter {
        override fun recordException(throwable: Throwable) = Unit
        override fun log(message: String) = Unit
        override fun setCustomKey(key: String, value: String) = Unit
    }

    private val json = Json { ignoreUnknownKeys = true }

    private class FakeCache : CardStrategyTagsCache {
        val store = mutableMapOf<String, CachedCardStrategyTagsEntry>()
        override suspend fun get(oracleId: String): CachedCardStrategyTagsEntry? = store[oracleId]
        override suspend fun insert(entry: CachedCardStrategyTagsEntry) {
            store[entry.oracleId] = entry
        }
    }

    private class FakeRemote(
        // A non-nullable behavior lambda (rather than a nullable `result: (() -> Dto?)?` defaulting
        // to null) is deliberate — a nullable-lambda-with-`?:`-fallback can't distinguish "no
        // behavior configured" from "behavior configured and legitimately returned null" (the exact
        // "remote has no row for this card yet" case this fake needs to model).
        val behavior: () -> CardStrategyTagsRowDto? = { throw IllegalStateException("Remote down") },
        val submitBehavior: () -> Unit = {},
    ) : CardStrategyTagsRemoteDataSourceContract {
        var callCount = 0
        var submitCallCount = 0
        var lastSubmittedOracleId: String? = null
        var lastSubmittedPayload: CardStrategyTagsPayloadDto? = null

        override suspend fun getByOracleId(oracleId: String): CardStrategyTagsRowDto? {
            callCount++
            return behavior()
        }

        override suspend fun submit(oracleId: String, payload: CardStrategyTagsPayloadDto) {
            submitCallCount++
            lastSubmittedOracleId = oracleId
            lastSubmittedPayload = payload
            submitBehavior()
        }
    }

    private fun repository(
        remote: CardStrategyTagsRemoteDataSourceContract,
        cache: CardStrategyTagsCache = FakeCache(),
        clock: () -> Long = { 2_000_000L },
    ): CardStrategyTagsRepositoryImpl = CardStrategyTagsRepositoryImpl(
        remote = remote,
        cache = cache,
        crashReporter = noopCrashReporter,
        dispatcherProvider = DispatcherProvider(),
        now = clock,
    )

    private fun row(oracleId: String, tags: List<String> = listOf("removal")) = CardStrategyTagsRowDto(
        oracleId = oracleId,
        payload = CardStrategyTagsPayloadDto(tags = tags),
        pipelineVersion = "1",
        generatedAt = "2026-07-21T00:00:00Z",
    )

    // ── Blank oracleId ───────────────────────────────────────────────────────

    @Test
    fun `given a blank oracleId when getStrategyTags then NotFound is returned without calling remote`() = runTest {
        val remote = FakeRemote()
        val repo = repository(remote)

        val result = repo.getStrategyTags("")

        assertEquals(CardStrategyTagsResult.NotFound, result)
        assertEquals(0, remote.callCount)
    }

    // ── Cache hit (fresh) ────────────────────────────────────────────────────

    @Test
    fun `given a fresh cache entry when getStrategyTags then remote is never called`() = runTest {
        val cache = FakeCache()
        val payloadJson = json.encodeToString(CardStrategyTagsPayloadDto.serializer(), CardStrategyTagsPayloadDto(tags = listOf("removal")))
        cache.store["oracle-1"] = CachedCardStrategyTagsEntry("oracle-1", payloadJson, "1", "2026-07-21T00:00:00Z", fetchedAt = 1_000_000L)
        val remote = FakeRemote()
        val repo = repository(remote, cache, clock = { 1_000_000L + 1_000L })

        val result = repo.getStrategyTags("oracle-1")

        assertIs<CardStrategyTagsResult.Found>(result)
        assertEquals(listOf(CardTag.REMOVAL), result.tags)
        assertTrue(!result.isStale)
        assertEquals(0, remote.callCount)
    }

    // ── Cache miss -> remote fetch ───────────────────────────────────────────

    @Test
    fun `given no cache entry when getStrategyTags then the remote row is fetched and cached`() = runTest {
        val cache = FakeCache()
        val remote = FakeRemote(behavior = { row("oracle-2") })
        val repo = repository(remote, cache)

        val result = repo.getStrategyTags("oracle-2")

        assertIs<CardStrategyTagsResult.Found>(result)
        assertEquals(listOf(CardTag.REMOVAL), result.tags)
        assertEquals(1, remote.callCount)
        assertTrue(cache.store.containsKey("oracle-2"))
    }

    @Test
    fun `given remote returns no row when getStrategyTags then NotFound is returned`() = runTest {
        val remote = FakeRemote(behavior = { null })
        val repo = repository(remote)

        val result = repo.getStrategyTags("oracle-unknown")

        assertEquals(CardStrategyTagsResult.NotFound, result)
    }

    // ── Stale cache handling ─────────────────────────────────────────────────

    @Test
    fun `given a stale cache entry when getStrategyTags succeeds remotely then the fresh remote result wins`() = runTest {
        val cache = FakeCache()
        val stalePayload = json.encodeToString(CardStrategyTagsPayloadDto.serializer(), CardStrategyTagsPayloadDto(tags = listOf("ramp")))
        cache.store["oracle-3"] = CachedCardStrategyTagsEntry("oracle-3", stalePayload, "1", "old", fetchedAt = 0L)
        val remote = FakeRemote(behavior = { row("oracle-3", tags = listOf("removal")) })
        // 14-day TTL exceeded.
        val repo = repository(remote, cache, clock = { 20L * 24 * 60 * 60 * 1000 })

        val result = repo.getStrategyTags("oracle-3")

        assertIs<CardStrategyTagsResult.Found>(result)
        assertEquals(listOf(CardTag.REMOVAL), result.tags)
        assertTrue(!result.isStale)
    }

    @Test
    fun `given a stale cache entry when remote fetch fails then the stale cache is served with isStale true`() = runTest {
        val cache = FakeCache()
        val stalePayload = json.encodeToString(CardStrategyTagsPayloadDto.serializer(), CardStrategyTagsPayloadDto(tags = listOf("removal")))
        cache.store["oracle-4"] = CachedCardStrategyTagsEntry("oracle-4", stalePayload, "1", "old", fetchedAt = 0L)
        val remote = FakeRemote() // no result set -> throws
        val repo = repository(remote, cache, clock = { 20L * 24 * 60 * 60 * 1000 })

        val result = repo.getStrategyTags("oracle-4")

        assertIs<CardStrategyTagsResult.Found>(result)
        assertEquals(listOf(CardTag.REMOVAL), result.tags)
        assertTrue(result.isStale)
    }

    @Test
    fun `given no cache at all when remote fetch fails then Error is returned`() = runTest {
        val remote = FakeRemote() // no result set -> throws
        val repo = repository(remote)

        val result = repo.getStrategyTags("oracle-5")

        assertIs<CardStrategyTagsResult.Error>(result)
    }

    // ── Dictionary-less tag key (TYPE tags, e.g. from TypeLineAnalyzer) ──────

    @Test
    fun `given a payload tag key not in TagDictionary when getStrategyTags then it is kept as a TYPE tag, not dropped`() = runTest {
        // "creature" mirrors a real TypeLineAnalyzer-emitted key: TagDictionary never registers
        // TYPE-category keys (see TagDictionary.kt's own doc comment), so a dictionary miss here
        // is the expected shape for a type-line tag, not a taxonomy-drift signal.
        val remote = FakeRemote(behavior = { row("oracle-6", tags = listOf("removal", "creature")) })
        val repo = repository(remote)

        val result = repo.getStrategyTags("oracle-6")

        assertIs<CardStrategyTagsResult.Found>(result)
        assertEquals(listOf(CardTag.REMOVAL, CardTag("creature", TagCategory.TYPE)), result.tags)
    }

    // ── submitStrategyTags (plan §8a addendum — device write-back) ──────────

    @Test
    fun `given a blank oracleId when submitStrategyTags then remote is never called`() = runTest {
        val remote = FakeRemote()
        val repo = repository(remote)

        repo.submitStrategyTags("", CardStrategyTagsSubmission(tags = listOf("removal")))

        assertEquals(0, remote.submitCallCount)
    }

    @Test
    fun `given a genuine submission when submitStrategyTags then the remote RPC is called with the correct payload shape`() = runTest {
        val remote = FakeRemote()
        val repo = repository(remote)

        repo.submitStrategyTags(
            "oracle-7",
            CardStrategyTagsSubmission(
                tags = listOf("removal", "ramp"),
                themes = mapOf("STAX" to 0.4f),
            ),
        )

        assertEquals(1, remote.submitCallCount)
        assertEquals("oracle-7", remote.lastSubmittedOracleId)
        val payload = remote.lastSubmittedPayload
        assertEquals(listOf("removal", "ramp"), payload?.tags)
        assertEquals(mapOf("STAX" to 0.4f), payload?.themes)
        assertEquals(listOf("device"), payload?.sources)
    }

    @Test
    fun `given a successful submit when submitStrategyTags then the local cache is also populated`() = runTest {
        val cache = FakeCache()
        val remote = FakeRemote()
        val repo = repository(remote, cache)

        repo.submitStrategyTags("oracle-8", CardStrategyTagsSubmission(tags = listOf("removal")))

        assertTrue(cache.store.containsKey("oracle-8"))
        assertEquals("device", cache.store["oracle-8"]?.pipelineVersion)
    }

    @Test
    fun `given the remote RPC throws when submitStrategyTags then the failure is swallowed, never propagated`() = runTest {
        val remote = FakeRemote(submitBehavior = { throw IllegalStateException("Rate limited") })
        val repo = repository(remote)

        // Must not throw.
        repo.submitStrategyTags("oracle-9", CardStrategyTagsSubmission(tags = listOf("removal")))

        assertEquals(1, remote.submitCallCount)
    }
}
