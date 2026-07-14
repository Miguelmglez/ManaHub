package com.mmg.manahub.feature.news.data

import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.NewsDao
import com.mmg.manahub.core.data.local.entity.ContentSourceEntity
import com.mmg.manahub.core.data.local.entity.NewsArticleEntity
import com.mmg.manahub.core.data.local.entity.NewsVideoEntity
import com.mmg.manahub.core.model.news.NewsFilterPrefs
import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.feature.news.data.parser.RssFeedParser
import com.mmg.manahub.feature.news.data.parser.YouTubeRssFeedParser
import com.mmg.manahub.feature.news.data.remote.FeedFetchResult
import com.mmg.manahub.feature.news.data.remote.NewsFeedService
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for [NewsRepositoryImpl] — News feature improvements Phase 1 (per-source
 * watermark + conditional GET + bounded concurrency), Phase 3 (failure visibility via
 * [com.mmg.manahub.core.model.news.RefreshResult]), and Phase 5 low findings F6 (allowlist
 * pruning on delete) / F9 (refresh-on-add covered indirectly through [refreshSource]).
 *
 * GROUP 1 — refreshAll: per-source staleness selection (fresh vs stale vs never-fetched)
 * GROUP 2 — refreshAll: 304 Not Modified short-circuit
 * GROUP 3 — refreshAll: failed fetch never advances the watermark (retried next cycle)
 * GROUP 4 — refreshAll: force=true bypasses the TTL but still honors conditional GET / 304
 * GROUP 5 — refreshAll: unconditional 7-day eviction
 * GROUP 6 — refreshAll: bounded concurrency (Semaphore(6))
 * GROUP 7 — refreshAll: RefreshResult never lies about success (all-fail / partial-fail / isolation)
 * GROUP 8 — refreshAll: Crashlytics non-fatal is keyed by source id, never the feed URL
 * GROUP 9 — refreshSource (F9)
 * GROUP 10 — addCustomSource: language persistence + HTTPS validation
 * GROUP 11 — deleteSource: F6 allowlist pruning
 * GROUP 12 — detectFeedLanguage
 * GROUP 13 — validateFeed
 * GROUP 14 — observeNews / observeSources / toggleSource
 */
class NewsRepositoryImplTest {

    private val newsDao = mockk<NewsDao>(relaxed = true)
    private val feedService = mockk<NewsFeedService>()
    private val rssParser = mockk<RssFeedParser>()
    private val ytParser = mockk<YouTubeRssFeedParser>()
    private val userPrefsDataStore = mockk<UserPreferencesDataStore>()

    private lateinit var repository: NewsRepositoryImpl

    private val oneHourMs = 60 * 60 * 1000L

    @Before
    fun setUp() {
        // fetchAndPersistSource's failure branch calls recordSafeNonFatal() outside runCatching,
        // which hits FirebaseCrashlytics.getInstance().
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        repository = NewsRepositoryImpl(newsDao, feedService, rssParser, ytParser, userPrefsDataStore)

        every { userPrefsDataStore.observeNewsFilters() } returns flowOf(NewsFilterPrefs.DEFAULT)
        every { rssParser.parse(any(), any(), any()) } returns emptyList()
        every { ytParser.parse(any(), any(), any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun articleSource(
        id: String = "src-1",
        lastFetchedAt: Long = 0L,
        etag: String? = null,
        lastModified: String? = null,
        enabled: Boolean = true,
    ) = ContentSourceEntity(
        id = id,
        name = "Source $id",
        feedUrl = "https://example.com/$id/feed",
        type = "ARTICLE",
        isEnabled = enabled,
        isDefault = false,
        language = "en",
        lastFetchedAt = lastFetchedAt,
        etag = etag,
        lastModified = lastModified,
    )

    private fun fetched(body: String = "<rss></rss>", etag: String? = "etag-new", lastModified: String? = "lm-new") =
        Result.success<FeedFetchResult>(FeedFetchResult.Fetched(body, etag, lastModified))

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — per-source staleness selection
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a source last fetched over 1 hour ago when refreshAll force=false then it is refetched`() = runTest {
        val staleSource = articleSource(id = "stale", lastFetchedAt = System.currentTimeMillis() - oneHourMs - 5_000)
        coEvery { newsDao.getEnabledSources() } returns listOf(staleSource)
        coEvery { feedService.fetchFeed("https://example.com/stale/feed", any(), any()) } returns fetched()

        repository.refreshAll(force = false)

        coVerify(exactly = 1) { feedService.fetchFeed("https://example.com/stale/feed", any(), any()) }
    }

    @Test
    fun `given a source last fetched under 1 hour ago when refreshAll force=false then it is skipped`() = runTest {
        val freshSource = articleSource(id = "fresh", lastFetchedAt = System.currentTimeMillis() - 1_000)
        coEvery { newsDao.getEnabledSources() } returns listOf(freshSource)

        repository.refreshAll(force = false)

        coVerify(exactly = 0) { feedService.fetchFeed(any(), any(), any()) }
    }

    @Test
    fun `given a source with lastFetchedAt=0 (never fetched) when refreshAll force=false then it is refetched`() = runTest {
        val neverFetched = articleSource(id = "never", lastFetchedAt = 0L)
        coEvery { newsDao.getEnabledSources() } returns listOf(neverFetched)
        coEvery { feedService.fetchFeed("https://example.com/never/feed", any(), any()) } returns fetched()

        repository.refreshAll(force = false)

        coVerify(exactly = 1) { feedService.fetchFeed("https://example.com/never/feed", any(), any()) }
    }

    @Test
    fun `given a mix of fresh and stale sources when refreshAll force=false then only the stale one is fetched`() = runTest {
        val fresh = articleSource(id = "fresh", lastFetchedAt = System.currentTimeMillis() - 1_000)
        val stale = articleSource(id = "stale", lastFetchedAt = 0L)
        coEvery { newsDao.getEnabledSources() } returns listOf(fresh, stale)
        coEvery { feedService.fetchFeed("https://example.com/stale/feed", any(), any()) } returns fetched()

        val result = repository.refreshAll(force = false).getOrThrow()

        coVerify(exactly = 0) { feedService.fetchFeed("https://example.com/fresh/feed", any(), any()) }
        coVerify(exactly = 1) { feedService.fetchFeed("https://example.com/stale/feed", any(), any()) }
        assertEquals(1, result.attempted)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — 304 Not Modified short-circuit
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a 304 response when refreshAll then the source is not parsed or upserted`() = runTest {
        val source = articleSource(id = "s1", lastFetchedAt = 0L, etag = "etag-old", lastModified = "lm-old")
        coEvery { newsDao.getEnabledSources() } returns listOf(source)
        coEvery { feedService.fetchFeed(any(), any(), any()) } returns Result.success(FeedFetchResult.NotModified)

        repository.refreshAll(force = false)

        coVerify(exactly = 0) { rssParser.parse(any(), any(), any()) }
        coVerify(exactly = 0) { newsDao.upsertArticles(any()) }
    }

    @Test
    fun `given a 304 response when refreshAll then the watermark timestamp still advances`() = runTest {
        val source = articleSource(id = "s1", lastFetchedAt = 0L, etag = "etag-old", lastModified = "lm-old")
        coEvery { newsDao.getEnabledSources() } returns listOf(source)
        coEvery { feedService.fetchFeed(any(), any(), any()) } returns Result.success(FeedFetchResult.NotModified)

        repository.refreshAll(force = false)

        // The existing etag/lastModified are re-passed unchanged; only fetchedAt actually advances.
        coVerify(exactly = 1) {
            newsDao.updateFetchWatermark("s1", any(), "etag-old", "lm-old")
        }
    }

    @Test
    fun `given a 304 response when refreshAll then it counts as notModified in RefreshResult`() = runTest {
        val source = articleSource(id = "s1", lastFetchedAt = 0L)
        coEvery { newsDao.getEnabledSources() } returns listOf(source)
        coEvery { feedService.fetchFeed(any(), any(), any()) } returns Result.success(FeedFetchResult.NotModified)

        val result = repository.refreshAll(force = false).getOrThrow()

        assertEquals(0, result.fetched)
        assertEquals(0, result.failed)
        assertEquals(1, result.notModified)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — failed fetch never advances the watermark
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a fetch failure when refreshAll then last_fetched_at is not updated`() = runTest {
        val source = articleSource(id = "s1", lastFetchedAt = 0L)
        coEvery { newsDao.getEnabledSources() } returns listOf(source)
        coEvery { feedService.fetchFeed(any(), any(), any()) } returns Result.failure(RuntimeException("boom"))

        repository.refreshAll(force = false)

        coVerify(exactly = 0) { newsDao.updateFetchWatermark(any(), any(), any(), any()) }
    }

    @Test
    fun `given a source type that throws while parsing when refreshAll then the watermark is not advanced either`() = runTest {
        val source = articleSource(id = "s1", lastFetchedAt = 0L)
        coEvery { newsDao.getEnabledSources() } returns listOf(source)
        coEvery { feedService.fetchFeed(any(), any(), any()) } returns fetched()
        every { rssParser.parse(any(), any(), any()) } throws RuntimeException("malformed xml")

        repository.refreshAll(force = false)

        coVerify(exactly = 0) { newsDao.updateFetchWatermark(any(), any(), any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — force=true bypasses the TTL but still honors conditional GET / 304
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given force=true when refreshAll then even a fresh source is refetched`() = runTest {
        val fresh = articleSource(id = "fresh", lastFetchedAt = System.currentTimeMillis() - 1_000)
        coEvery { newsDao.getEnabledSources() } returns listOf(fresh)
        coEvery { feedService.fetchFeed(any(), any(), any()) } returns fetched()

        repository.refreshAll(force = true)

        coVerify(exactly = 1) { feedService.fetchFeed("https://example.com/fresh/feed", any(), any()) }
    }

    @Test
    fun `given force=true when refreshAll then conditional headers are still sent for every source`() = runTest {
        val source = articleSource(id = "s1", lastFetchedAt = System.currentTimeMillis(), etag = "etag-1", lastModified = "lm-1")
        coEvery { newsDao.getEnabledSources() } returns listOf(source)
        coEvery { feedService.fetchFeed(any(), any(), any()) } returns fetched()

        repository.refreshAll(force = true)

        coVerify(exactly = 1) {
            feedService.fetchFeed("https://example.com/s1/feed", "etag-1", "lm-1")
        }
    }

    @Test
    fun `given force=true and the source returns 304 then it still short-circuits to NotModified`() = runTest {
        val source = articleSource(id = "s1", lastFetchedAt = System.currentTimeMillis())
        coEvery { newsDao.getEnabledSources() } returns listOf(source)
        coEvery { feedService.fetchFeed(any(), any(), any()) } returns Result.success(FeedFetchResult.NotModified)

        val result = repository.refreshAll(force = true).getOrThrow()

        assertEquals(1, result.notModified)
        coVerify(exactly = 0) { rssParser.parse(any(), any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — unconditional 7-day eviction
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given refreshAll is called then eviction runs even when nothing needs fetching`() = runTest {
        val fresh = articleSource(id = "fresh", lastFetchedAt = System.currentTimeMillis())
        coEvery { newsDao.getEnabledSources() } returns listOf(fresh)

        repository.refreshAll(force = false)

        coVerify(exactly = 1) { newsDao.evictArticlesBefore(any()) }
        coVerify(exactly = 1) { newsDao.evictVideosBefore(any()) }
    }

    @Test
    fun `given refreshAll is called with force=true then eviction still runs exactly once`() = runTest {
        coEvery { newsDao.getEnabledSources() } returns emptyList()

        repository.refreshAll(force = true)

        coVerify(exactly = 1) { newsDao.evictArticlesBefore(any()) }
        coVerify(exactly = 1) { newsDao.evictVideosBefore(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — bounded concurrency (Semaphore(6))
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given more sources than the concurrency limit when refreshAll then at most 6 fetches run at once`() = runTest {
        val sources = (1..10).map { i -> articleSource(id = "s$i", lastFetchedAt = 0L) }
        coEvery { newsDao.getEnabledSources() } returns sources

        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        coEvery { feedService.fetchFeed(any(), any(), any()) } coAnswers {
            val current = active.incrementAndGet()
            maxActive.updateAndGet { prev -> maxOf(prev, current) }
            delay(50)
            active.decrementAndGet()
            fetched()
        }

        repository.refreshAll(force = false)

        assertTrue(
            "no more than MAX_CONCURRENT_FETCHES (6) fetches should be in flight at once, was ${maxActive.get()}",
            maxActive.get() <= 6,
        )
    }

    @Test
    fun `given 10 stale sources when refreshAll then all 10 are eventually fetched`() = runTest {
        val sources = (1..10).map { i -> articleSource(id = "s$i", lastFetchedAt = 0L) }
        coEvery { newsDao.getEnabledSources() } returns sources
        coEvery { feedService.fetchFeed(any(), any(), any()) } coAnswers {
            delay(10)
            fetched()
        }

        val result = repository.refreshAll(force = false).getOrThrow()

        assertEquals(10, result.fetched)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — RefreshResult never lies about success
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given every source fails when refreshAll then failed equals attempted`() = runTest {
        val sources = (1..3).map { i -> articleSource(id = "s$i", lastFetchedAt = 0L) }
        coEvery { newsDao.getEnabledSources() } returns sources
        coEvery { feedService.fetchFeed(any(), any(), any()) } returns Result.failure(RuntimeException("down"))

        val result = repository.refreshAll(force = false).getOrThrow()

        assertEquals(3, result.attempted)
        assertEquals(3, result.failed)
        assertEquals(0, result.fetched)
        assertEquals(0, result.notModified)
        assertTrue("the overall Result must still be success — per-source failures are isolated", true)
    }

    @Test
    fun `given a partial failure when refreshAll then fetched failed and notModified sum correctly`() = runTest {
        val ok = articleSource(id = "ok", lastFetchedAt = 0L)
        val notMod = articleSource(id = "notmod", lastFetchedAt = 0L)
        val bad = articleSource(id = "bad", lastFetchedAt = 0L)
        coEvery { newsDao.getEnabledSources() } returns listOf(ok, notMod, bad)
        coEvery { feedService.fetchFeed("https://example.com/ok/feed", any(), any()) } returns fetched()
        coEvery { feedService.fetchFeed("https://example.com/notmod/feed", any(), any()) } returns
            Result.success(FeedFetchResult.NotModified)
        coEvery { feedService.fetchFeed("https://example.com/bad/feed", any(), any()) } returns
            Result.failure(RuntimeException("dead feed"))

        val result = repository.refreshAll(force = false).getOrThrow()

        assertEquals(1, result.fetched)
        assertEquals(1, result.notModified)
        assertEquals(1, result.failed)
        assertEquals(3, result.attempted)
    }

    @Test
    fun `given one dead feed among several when refreshAll then the healthy sources are still parsed and upserted`() = runTest {
        val healthy1 = articleSource(id = "healthy1", lastFetchedAt = 0L)
        val dead = articleSource(id = "dead", lastFetchedAt = 0L)
        val healthy2 = articleSource(id = "healthy2", lastFetchedAt = 0L)
        coEvery { newsDao.getEnabledSources() } returns listOf(healthy1, dead, healthy2)
        coEvery { feedService.fetchFeed("https://example.com/healthy1/feed", any(), any()) } returns fetched()
        coEvery { feedService.fetchFeed("https://example.com/healthy2/feed", any(), any()) } returns fetched()
        coEvery { feedService.fetchFeed("https://example.com/dead/feed", any(), any()) } returns
            Result.failure(RuntimeException("dead"))
        val article = NewsArticleEntity(
            id = "a1", title = "T", description = "D", imageUrl = null, publishedAt = 0L,
            sourceName = "S", sourceId = "healthy1", url = "https://example.com/a1", author = null,
        )
        every { rssParser.parse(any(), "healthy1", any()) } returns listOf(article)
        every { rssParser.parse(any(), "healthy2", any()) } returns listOf(article.copy(id = "a2", sourceId = "healthy2"))

        repository.refreshAll(force = false)

        coVerify(exactly = 1) { newsDao.upsertArticles(match { it.any { a -> a.sourceId == "healthy1" } }) }
        coVerify(exactly = 1) { newsDao.upsertArticles(match { it.any { a -> a.sourceId == "healthy2" } }) }
    }

    @Test
    fun `given refreshAll throws before any source is contacted then the outer Result is a failure`() = runTest {
        coEvery { newsDao.getEnabledSources() } throws RuntimeException("db exploded")

        val result = repository.refreshAll(force = false)

        assertTrue(result.isFailure)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — Crashlytics non-fatal keyed by source id, never the feed URL
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a fetch failure when refreshAll then Crashlytics receives the source id, not the feed URL`() = runTest {
        val source = articleSource(id = "flaky-source-42", lastFetchedAt = 0L)
        coEvery { newsDao.getEnabledSources() } returns listOf(source)
        coEvery { feedService.fetchFeed(any(), any(), any()) } returns Result.failure(RuntimeException("timeout"))

        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
        val slot: CapturingSlot<Throwable> = slot()
        every { crashlytics.recordException(capture(slot)) } returns Unit

        repository.refreshAll(force = false)

        val recordedMessage = slot.captured.message.orEmpty()
        assertTrue("must include the source id", recordedMessage.contains("flaky-source-42"))
        assertFalse(
            "must never include the feed URL (custom user feeds must not leak into Crashlytics)",
            recordedMessage.contains("example.com"),
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 9 — refreshSource (F9)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a known source id when refreshSource then it fetches and returns success`() = runTest {
        val source = articleSource(id = "s1", lastFetchedAt = 0L)
        coEvery { newsDao.getSourceById("s1") } returns source
        coEvery { feedService.fetchFeed(any(), any(), any()) } returns fetched()

        val result = repository.refreshSource("s1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { feedService.fetchFeed("https://example.com/s1/feed", any(), any()) }
    }

    @Test
    fun `given refreshSource bypasses the staleness check then even a fresh source is fetched immediately`() = runTest {
        val fresh = articleSource(id = "s1", lastFetchedAt = System.currentTimeMillis())
        coEvery { newsDao.getSourceById("s1") } returns fresh
        coEvery { feedService.fetchFeed(any(), any(), any()) } returns fetched()

        repository.refreshSource("s1")

        coVerify(exactly = 1) { feedService.fetchFeed(any(), any(), any()) }
    }

    @Test
    fun `given an unknown source id when refreshSource then it fails without calling feedService`() = runTest {
        coEvery { newsDao.getSourceById("missing") } returns null

        val result = repository.refreshSource("missing")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { feedService.fetchFeed(any(), any(), any()) }
    }

    @Test
    fun `given the fetch fails when refreshSource then it returns failure`() = runTest {
        val source = articleSource(id = "s1", lastFetchedAt = 0L)
        coEvery { newsDao.getSourceById("s1") } returns source
        coEvery { feedService.fetchFeed(any(), any(), any()) } returns Result.failure(RuntimeException("down"))

        val result = repository.refreshSource("s1")

        assertTrue(result.isFailure)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 10 — addCustomSource: language persistence + HTTPS validation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a custom source added with language es then the persisted entity has language es`() = runTest {
        val slot = slot<List<ContentSourceEntity>>()
        coEvery { newsDao.insertSourcesIfAbsent(capture(slot)) } returns Unit

        val result = repository.addCustomSource(
            name = "Spanish Blog",
            feedUrl = "https://example.com/es/feed",
            type = SourceType.ARTICLE,
            language = "es",
        )

        assertTrue(result.isSuccess)
        assertEquals("es", result.getOrThrow().language)
        assertEquals("es", slot.captured.single().language)
    }

    @Test
    fun `given a custom source added with language de then the persisted entity has language de, not a hardcoded en`() = runTest {
        val result = repository.addCustomSource(
            name = "German Blog",
            feedUrl = "https://example.com/de/feed",
            type = SourceType.ARTICLE,
            language = "de",
        )

        assertEquals("de", result.getOrThrow().language)
    }

    @Test
    fun `given a feed URL that is not HTTPS when addCustomSource then it fails and nothing is persisted`() = runTest {
        val result = repository.addCustomSource(
            name = "Insecure",
            feedUrl = "http://example.com/feed",
            type = SourceType.ARTICLE,
            language = "en",
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { newsDao.insertSourcesIfAbsent(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 11 — deleteSource: F6 allowlist pruning
    // ══════════════════════════════════════════════════════════════════════════

    // NOTE (fix batch, 2026-07-14): pruneSourceFromFilterAllowlist now delegates the whole
    // read-modify-write to UserPreferencesDataStore.pruneNewsFilterSourceId in a SINGLE atomic
    // `edit{}` transaction (see that method's KDoc) instead of round-tripping through
    // observeNewsFilters().first() + setNewsFilters() here — a stale-snapshot read-then-write
    // could clobber a concurrent filter-apply's language/type change. The "was the id actually
    // in the allowlist" branching that used to be asserted at THIS layer now lives inside
    // pruneNewsFilterSourceId itself, so these tests only assert that deleteSource calls (or
    // doesn't call) that method with the right id.

    @Test
    fun `given deleting a custom source then pruneNewsFilterSourceId is called with its id`() = runTest {
        val custom = articleSource(id = "custom-1").copy(isDefault = false)
        coEvery { newsDao.getAllSources() } returns listOf(custom)
        coEvery { userPrefsDataStore.pruneNewsFilterSourceId(any()) } returns Unit

        repository.deleteSource("custom-1")

        coVerify(exactly = 1) { newsDao.deleteSource(custom) }
        coVerify(exactly = 1) { userPrefsDataStore.pruneNewsFilterSourceId("custom-1") }
    }

    @Test
    fun `given deleting a default source then it is not deleted and pruneNewsFilterSourceId is never called`() = runTest {
        val default = articleSource(id = "default-1").copy(isDefault = true)
        coEvery { newsDao.getAllSources() } returns listOf(default)

        repository.deleteSource("default-1")

        coVerify(exactly = 0) { newsDao.deleteSource(any()) }
        coVerify(exactly = 0) { userPrefsDataStore.pruneNewsFilterSourceId(any()) }
    }

    @Test
    fun `given deleting a source id that does not exist then it is a no-op`() = runTest {
        coEvery { newsDao.getAllSources() } returns emptyList()

        repository.deleteSource("ghost")

        coVerify(exactly = 0) { newsDao.deleteSource(any()) }
        coVerify(exactly = 0) { userPrefsDataStore.pruneNewsFilterSourceId(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 12 — detectFeedLanguage
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a successful fetch with a detectable channel language then detectFeedLanguage returns it`() = runTest {
        coEvery { feedService.fetchFeed("https://example.com/feed") } returns fetched(body = "<rss/>")
        every { rssParser.detectChannelLanguage("<rss/>") } returns "es"

        val language = repository.detectFeedLanguage("https://example.com/feed")

        assertEquals("es", language)
    }

    @Test
    fun `given fetchFeed returns NotModified when detectFeedLanguage then it returns null`() = runTest {
        coEvery { feedService.fetchFeed("https://example.com/feed") } returns Result.success(FeedFetchResult.NotModified)

        val language = repository.detectFeedLanguage("https://example.com/feed")

        assertNull(language)
    }

    @Test
    fun `given fetchFeed fails when detectFeedLanguage then it returns null instead of throwing`() = runTest {
        coEvery { feedService.fetchFeed("https://example.com/feed") } returns Result.failure(RuntimeException("down"))

        val language = repository.detectFeedLanguage("https://example.com/feed")

        assertNull(language)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 13 — validateFeed
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a feed that parses to N items when validateFeed then it succeeds with that count`() = runTest {
        coEvery { feedService.fetchFeed("https://example.com/feed") } returns fetched(body = "<rss/>")
        val items = listOf(
            NewsArticleEntity("1", "T1", "D", null, 0L, "S", "validate", "u1", null),
            NewsArticleEntity("2", "T2", "D", null, 0L, "S", "validate", "u2", null),
        )
        every { rssParser.parse("<rss/>", "validate", "Validate") } returns items

        val result = repository.validateFeed("https://example.com/feed", SourceType.ARTICLE)

        assertEquals(2, result.getOrThrow())
    }

    @Test
    fun `given a feed that parses to zero items when validateFeed then it fails with a descriptive message`() = runTest {
        coEvery { feedService.fetchFeed("https://example.com/feed") } returns fetched(body = "<rss/>")
        every { rssParser.parse("<rss/>", "validate", "Validate") } returns emptyList()

        val result = repository.validateFeed("https://example.com/feed", SourceType.ARTICLE)

        assertTrue(result.isFailure)
        assertEquals("No items found in feed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `given fetchFeed unexpectedly returns NotModified when validateFeed then it fails defensively`() = runTest {
        coEvery { feedService.fetchFeed("https://example.com/feed") } returns Result.success(FeedFetchResult.NotModified)

        val result = repository.validateFeed("https://example.com/feed", SourceType.ARTICLE)

        assertTrue(result.isFailure)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 14 — observeNews / observeSources / toggleSource
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given articles and videos when observeNews then both are merged sorted by publishedAt desc`() = runTest {
        val article = NewsArticleEntity("a1", "Article", "D", null, 1_000L, "S", "src", "u1", null)
        val video = NewsVideoEntity("v1", "Video", "D", null, 2_000L, "S", "src", "u2", "Chan")
        every { newsDao.observeArticles() } returns flowOf(listOf(article))
        every { newsDao.observeVideos() } returns flowOf(listOf(video))

        repository.observeNews().test {
            val items = awaitItem()
            assertEquals(2, items.size)
            assertEquals("v1", items[0].id) // video is newer (2000) so it sorts first
            assertEquals("a1", items[1].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given sources in the DAO when observeSources then they are mapped to domain ContentSource`() = runTest {
        val entity = articleSource(id = "s1")
        every { newsDao.observeSources() } returns flowOf(listOf(entity))

        repository.observeSources().test {
            val sources = awaitItem()
            assertEquals(1, sources.size)
            assertEquals("s1", sources[0].id)
            assertEquals(SourceType.ARTICLE, sources[0].type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given toggleSource is called then it delegates to the DAO with the same id and enabled flag`() = runTest {
        coEvery { newsDao.setSourceEnabled("s1", false) } returns Unit

        repository.toggleSource("s1", false)

        coVerify(exactly = 1) { newsDao.setSourceEnabled("s1", false) }
    }
}
