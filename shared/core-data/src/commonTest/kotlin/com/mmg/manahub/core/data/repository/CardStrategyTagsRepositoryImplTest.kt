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

    /** Batch-aware fake: [rows] is the whole simulated `card_strategy_tags` table, so an id with
     *  no entry models a genuine "no row yet" miss (a SHORT result, never an error). */
    private class FakeBatchRemote(
        private val rows: Map<String, CardStrategyTagsRowDto> = emptyMap(),
        private val failChunks: Set<Int> = emptySet(),
    ) : CardStrategyTagsRemoteDataSourceContract {
        val requestedChunks = mutableListOf<List<String>>()

        override suspend fun getByOracleId(oracleId: String): CardStrategyTagsRowDto? = rows[oracleId]

        override suspend fun getByOracleIds(oracleIds: List<String>): List<CardStrategyTagsRowDto> {
            val index = requestedChunks.size
            requestedChunks += oracleIds
            if (index in failChunks) throw IllegalStateException("Remote down")
            return oracleIds.mapNotNull { rows[it] }
        }

        override suspend fun submit(oracleId: String, payload: CardStrategyTagsPayloadDto) = Unit
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

        override suspend fun getByOracleIds(oracleIds: List<String>): List<CardStrategyTagsRowDto> {
            callCount++
            return listOfNotNull(behavior())
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

    // ══════════════════════════════════════════════════════════════════════════
    //  getStrategyTagsBatch — bulk collection hydration (2026-09-07)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given more ids than one chunk when getStrategyTagsBatch then requests are chunked and every id is resolved`() = runTest {
        val ids = (1..250).map { "oracle-$it" }
        val remote = FakeBatchRemote(rows = ids.associateWith { row(it) })
        val repo = repository(remote)

        val results = repo.getStrategyTagsBatch(ids.toSet())

        assertEquals(listOf(100, 100, 50), remote.requestedChunks.map { it.size })
        assertEquals(250, results.size)
        assertTrue(results.values.all { it is CardStrategyTagsResult.Found })
    }

    @Test
    fun `given some ids already cached fresh when getStrategyTagsBatch then only the rest hit the network`() = runTest {
        val cache = FakeCache()
        cache.store["oracle-1"] = CachedCardStrategyTagsEntry(
            oracleId = "oracle-1",
            payloadJson = json.encodeToString(
                CardStrategyTagsPayloadDto.serializer(),
                CardStrategyTagsPayloadDto(tags = listOf("ramp")),
            ),
            pipelineVersion = "1",
            generatedAt = "2026-07-21T00:00:00Z",
            fetchedAt = 1_999_000L,
        )
        val remote = FakeBatchRemote(rows = mapOf("oracle-2" to row("oracle-2")))
        val repo = repository(remote, cache)

        val results = repo.getStrategyTagsBatch(setOf("oracle-1", "oracle-2"))

        assertEquals(listOf(listOf("oracle-2")), remote.requestedChunks)
        assertEquals(listOf("ramp"), (results.getValue("oracle-1") as CardStrategyTagsResult.Found).tags.map { it.key })
        assertIs<CardStrategyTagsResult.Found>(results.getValue("oracle-2"))
    }

    @Test
    fun `given the table has no row for some ids when getStrategyTagsBatch then those are NotFound and the rest still resolve`() = runTest {
        val remote = FakeBatchRemote(rows = mapOf("oracle-1" to row("oracle-1")))
        val cache = FakeCache()
        val repo = repository(remote, cache)

        val results = repo.getStrategyTagsBatch(setOf("oracle-1", "oracle-missing"))

        assertIs<CardStrategyTagsResult.Found>(results.getValue("oracle-1"))
        assertIs<CardStrategyTagsResult.NotFound>(results.getValue("oracle-missing"))
        // A short result is "those ids have no row", never "stop early" -- the hit is still cached,
        // and the miss deliberately writes NO cache row (submitStrategyTags is what caches those).
        assertTrue(cache.store.containsKey("oracle-1"))
        assertTrue(!cache.store.containsKey("oracle-missing"))
    }

    @Test
    fun `given one chunk fails when getStrategyTagsBatch then only that chunk degrades and later chunks still run`() = runTest {
        val ids = (1..150).map { "oracle-$it" }
        val remote = FakeBatchRemote(rows = ids.associateWith { row(it) }, failChunks = setOf(0))
        val repo = repository(remote)

        val results = repo.getStrategyTagsBatch(ids.toSet())

        assertEquals(2, remote.requestedChunks.size)
        assertEquals(150, results.size)
        assertIs<CardStrategyTagsResult.Error>(results.getValue("oracle-1"))
        assertIs<CardStrategyTagsResult.Found>(results.getValue("oracle-150"))
    }

    @Test
    fun `given a failing chunk with a stale cache entry when getStrategyTagsBatch then the stale entry is served`() = runTest {
        val cache = FakeCache()
        cache.store["oracle-1"] = CachedCardStrategyTagsEntry(
            oracleId = "oracle-1",
            payloadJson = json.encodeToString(
                CardStrategyTagsPayloadDto.serializer(),
                CardStrategyTagsPayloadDto(tags = listOf("ramp")),
            ),
            pipelineVersion = "1",
            generatedAt = "2026-07-21T00:00:00Z",
            fetchedAt = 0L,
        )
        val remote = FakeBatchRemote(failChunks = setOf(0))
        // Clock well past the 14-day freshness window, so fetchedAt = 0 is genuinely expired.
        val repo = repository(remote, cache, clock = { 2_000_000_000L })

        val found = repo.getStrategyTagsBatch(setOf("oracle-1")).getValue("oracle-1")

        assertIs<CardStrategyTagsResult.Found>(found)
        assertTrue(found.isStale)
    }

    @Test
    fun `given blank ids when getStrategyTagsBatch then they are dropped and no request is made`() = runTest {
        val remote = FakeBatchRemote()
        val repo = repository(remote)

        val results = repo.getStrategyTagsBatch(setOf("", "   "))

        assertTrue(results.isEmpty())
        assertTrue(remote.requestedChunks.isEmpty())
    }
}
