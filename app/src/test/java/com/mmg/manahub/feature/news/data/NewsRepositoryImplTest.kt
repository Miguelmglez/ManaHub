package com.mmg.manahub.feature.news.data

import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.LegacyNewsFilters
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.NewsDao
import com.mmg.manahub.core.data.local.entity.ContentSourceEntity
import com.mmg.manahub.core.data.local.entity.NewsArticleEntity
import com.mmg.manahub.core.data.local.entity.NewsSavedItemEntity
import com.mmg.manahub.core.data.local.entity.NewsVideoEntity
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.ResolvedSource
import com.mmg.manahub.core.model.news.SourceResolveError
import com.mmg.manahub.core.model.news.SourceResolveException
import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.feature.news.data.local.DefaultSources
import com.mmg.manahub.feature.news.data.parser.RssFeedParser
import com.mmg.manahub.feature.news.data.parser.YouTubeRssFeedParser
import com.mmg.manahub.feature.news.data.remote.FeedFetchResult
import com.mmg.manahub.feature.news.data.remote.FetchedPage
import com.mmg.manahub.feature.news.data.remote.NewsFeedService
import com.mmg.manahub.feature.news.data.remote.PageFetchException
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.concurrent.atomic.AtomicInteger
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
 * GROUP 11 — deleteSource: custom sources only
 * GROUP 14 — observeNews / observeSources / setSourceFollowed
 * GROUP 15 — reconcileDefaultSources (dead-source cleanup fix, 2026-07-16): feedUrl drift
 *            rewrite + watermark reset, retired-id cleanup, idempotency, custom sources untouched
 * GROUP 16 — saved items (snapshot mapping, save/unsave, id set)
 * GROUP 17 — one-time legacy filter → follow migration
 * GROUP 18 — default follow policy when seeding + site URLs (catalog for defaults, feed link for custom)
 * GROUP 19 — resolveSource ("paste any URL")
 * GROUP 20 — followResolvedSource (dedupe, follow, refresh-on-follow)
 */
class NewsRepositoryImplTest {

    private val newsDao = mockk<NewsDao>(relaxed = true)
    private val feedService = mockk<NewsFeedService>()
    private val rssParser = mockk<RssFeedParser>()
    private val ytParser = mockk<YouTubeRssFeedParser>()
    private val userPrefsDataStore = mockk<UserPreferencesDataStore>()

    private lateinit var repository: NewsRepositoryImpl

    private val oneHourMs = 60 * 60 * 1000L
    private var deviceLanguage = "en"

    @Before
    fun setUp() {
        // fetchAndPersistSource's failure branch calls recordSafeNonFatal() outside runCatching,
        // which hits FirebaseCrashlytics.getInstance().
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        repository = NewsRepositoryImpl(
            newsDao, feedService, rssParser, ytParser, userPrefsDataStore,
            deviceLanguage = { deviceLanguage },
            nowMs = { System.currentTimeMillis() },
        )

        coEvery { userPrefsDataStore.isNewsFollowMigrationDone() } returns true
        coEvery { userPrefsDataStore.completeNewsFollowMigration() } returns Unit
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
    //  GROUP 11 — deleteSource: custom only, no filter bookkeeping any more
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given deleting a custom source then it is deleted`() = runTest {
        val custom = articleSource(id = "custom-1").copy(isDefault = false)
        coEvery { newsDao.getSourceById("custom-1") } returns custom

        repository.deleteSource("custom-1")

        coVerify(exactly = 1) { newsDao.deleteSource(custom) }
    }

    @Test
    fun `given deleting a default source then it is not deleted`() = runTest {
        val default = articleSource(id = "default-1").copy(isDefault = true)
        coEvery { newsDao.getSourceById("default-1") } returns default

        repository.deleteSource("default-1")

        coVerify(exactly = 0) { newsDao.deleteSource(any()) }
    }

    @Test
    fun `given deleting a source id that does not exist then it is a no-op`() = runTest {
        coEvery { newsDao.getSourceById("ghost") } returns null

        repository.deleteSource("ghost")

        coVerify(exactly = 0) { newsDao.deleteSource(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 14 — observeNews / observeSources / setSourceFollowed
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
    fun `given setSourceFollowed is called then it delegates to the DAO with the same id and flag`() = runTest {
        coEvery { newsDao.setSourceEnabled("s1", false) } returns Unit

        repository.setSourceFollowed("s1", false)

        coVerify(exactly = 1) { newsDao.setSourceEnabled("s1", false) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 15 — reconcileDefaultSources (dead-source cleanup fix, 2026-07-16)
    //
    //  insertSourcesIfAbsent's OnConflictStrategy.IGNORE means editing/removing a row in
    //  DefaultSources.kt has no effect on an install that already seeded the old row under the
    //  same id — reconcileDefaultSources (called from refreshAll, right after
    //  insertSourcesIfAbsent) is the reconciliation step that fixes that for existing installs.
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a persisted default source whose feedUrl differs from the catalog when refreshAll then it is rewritten and its watermark is reset`() = runTest {
        val stalePersisted = ContentSourceEntity(
            id = "default_article_mtggoldfish",
            name = "MTGGoldfish",
            feedUrl = "https://old.example.com/feed",
            type = "ARTICLE",
            isDefault = true,
            language = "en",
            lastFetchedAt = 123_456L,
            etag = "old-etag",
            lastModified = "old-lm",
        )
        coEvery { newsDao.getAllSources() } returns listOf(stalePersisted)
        coEvery { newsDao.getEnabledSources() } returns emptyList()

        repository.refreshAll(force = false)

        coVerify(exactly = 1) {
            newsDao.updateSource(
                match {
                    it.id == "default_article_mtggoldfish" &&
                        it.feedUrl == "https://www.mtggoldfish.com/feed" &&
                        it.etag == null &&
                        it.lastModified == null &&
                        it.lastFetchedAt == 0L
                }
            )
        }
    }

    @Test
    fun `given a feedUrl-drift reconciliation when refreshAll then the user's isEnabled preference is preserved`() = runTest {
        val stalePersisted = ContentSourceEntity(
            id = "default_article_mtggoldfish",
            name = "MTGGoldfish",
            feedUrl = "https://old.example.com/feed",
            type = "ARTICLE",
            isEnabled = false,
            isDefault = true,
            language = "en",
        )
        coEvery { newsDao.getAllSources() } returns listOf(stalePersisted)
        coEvery { newsDao.getEnabledSources() } returns emptyList()

        repository.refreshAll(force = false)

        coVerify(exactly = 1) { newsDao.updateSource(match { it.isEnabled == false }) }
    }

    @Test
    fun `given a persisted default source with a retired id when refreshAll then it is deleted via deleteSourcesByIds`() = runTest {
        val retired = ContentSourceEntity(
            id = "default_article_edhrec",
            name = "EDHREC",
            feedUrl = "https://edhrec.com/articles/feed",
            type = "ARTICLE",
            isDefault = true,
            language = "en",
        )
        coEvery { newsDao.getAllSources() } returns listOf(retired)
        coEvery { newsDao.getEnabledSources() } returns emptyList()

        repository.refreshAll(force = false)

        coVerify(exactly = 1) { newsDao.deleteSourcesByIds(listOf("default_article_edhrec")) }
        coVerify(exactly = 0) { newsDao.updateSource(any()) }
    }

    @Test
    fun `given multiple retired ids among healthy sources when refreshAll then only the retired ones are deleted`() = runTest {
        val healthy = DefaultSources.all.first { it.id == "default_article_mtggoldfish" }
        val retired1 = healthy.copy(id = "default_article_edhrec", feedUrl = "https://edhrec.com/articles/feed")
        val retired2 = healthy.copy(id = "default_article_cranial", feedUrl = "https://cranial-insertion.com/feed")
        coEvery { newsDao.getAllSources() } returns listOf(healthy, retired1, retired2)
        coEvery { newsDao.getEnabledSources() } returns emptyList()

        repository.refreshAll(force = false)

        coVerify(exactly = 1) {
            newsDao.deleteSourcesByIds(
                match { it.toSet() == setOf("default_article_edhrec", "default_article_cranial") }
            )
        }
    }

    @Test
    fun `given persisted default sources that exactly match the catalog when refreshAll then nothing is updated or deleted`() = runTest {
        val upToDate = DefaultSources.all.first().copy(lastFetchedAt = 111L)
        coEvery { newsDao.getAllSources() } returns listOf(upToDate)
        coEvery { newsDao.getEnabledSources() } returns emptyList()

        repository.refreshAll(force = false)

        coVerify(exactly = 0) { newsDao.updateSource(any()) }
        coVerify(exactly = 0) { newsDao.deleteSourcesByIds(any()) }
    }

    @Test
    fun `given reconciliation already applied when refreshAll runs a second time then it is a no-op (idempotent)`() = runTest {
        // Simulates the state AFTER a prior reconciliation: the corrected feedUrl is already
        // persisted, and the retired source is already gone from the DB.
        val alreadyReconciled = DefaultSources.all.first { it.id == "default_video_nitpicking" }
        coEvery { newsDao.getAllSources() } returns listOf(alreadyReconciled)
        coEvery { newsDao.getEnabledSources() } returns emptyList()

        repository.refreshAll(force = false)
        repository.refreshAll(force = false)

        coVerify(exactly = 0) { newsDao.updateSource(any()) }
        coVerify(exactly = 0) { newsDao.deleteSourcesByIds(any()) }
    }

    @Test
    fun `given a custom source with a stale-looking feedUrl when refreshAll then reconciliation never touches it`() = runTest {
        val custom = articleSource(id = "custom_abc123")
            .copy(isDefault = false, feedUrl = "https://custom.example.com/feed")
        coEvery { newsDao.getAllSources() } returns listOf(custom)
        coEvery { newsDao.getEnabledSources() } returns emptyList()

        repository.refreshAll(force = false)

        coVerify(exactly = 0) { newsDao.updateSource(any()) }
        coVerify(exactly = 0) { newsDao.deleteSourcesByIds(any()) }
    }

    @Test
    fun `given a mix of drifted retired and untouched sources when refreshAll then each is handled independently`() = runTest {
        val drifted = ContentSourceEntity(
            id = "default_article_mtggoldfish",
            name = "MTGGoldfish",
            feedUrl = "https://old.example.com/feed",
            type = "ARTICLE",
            isDefault = true,
            language = "en",
        )
        val retired = ContentSourceEntity(
            id = "default_article_gatheringmagic",
            name = "GatheringMagic",
            feedUrl = "https://www.gatheringmagic.com/feed/",
            type = "ARTICLE",
            isDefault = true,
            language = "en",
        )
        val untouched = DefaultSources.all.first { it.id == "default_article_scg" }
        val custom = articleSource(id = "custom_xyz").copy(isDefault = false)
        coEvery { newsDao.getAllSources() } returns listOf(drifted, retired, untouched, custom)
        coEvery { newsDao.getEnabledSources() } returns emptyList()

        repository.refreshAll(force = false)

        coVerify(exactly = 1) { newsDao.updateSource(match { it.id == "default_article_mtggoldfish" }) }
        coVerify(exactly = 1) { newsDao.deleteSourcesByIds(listOf("default_article_gatheringmagic")) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 16 — saved items
    // ══════════════════════════════════════════════════════════════════════════

    private val savedArticle = NewsItem.Article(
        id = "a1", title = "Title", description = "Desc", imageUrl = "https://img", publishedAt = 10L,
        sourceName = "Blog", sourceId = "src-1", url = "https://example.com/a1", author = "Ann",
    )

    private val savedVideo = NewsItem.Video(
        id = "v1", title = "Video", description = "Desc", imageUrl = null, publishedAt = 20L,
        sourceName = "Chan", sourceId = "src-2", url = "https://www.youtube.com/watch?v=v1",
        videoId = "v1", channelName = "Channel", duration = "12:00",
    )

    @Test
    fun `given an article when save then a snapshot entity with savedAt is inserted`() = runTest {
        val slot = slot<NewsSavedItemEntity>()
        coEvery { newsDao.insertSaved(capture(slot)) } returns Unit
        val before = System.currentTimeMillis()

        repository.save(savedArticle)

        with(slot.captured) {
            assertEquals("a1", id)
            assertEquals("ARTICLE", kind)
            assertEquals("Ann", author)
            assertNull(videoId)
            assertTrue(savedAt >= before)
        }
    }

    @Test
    fun `given a saved video entity when observeSaved then it maps back to a Video`() = runTest {
        val entity = NewsSavedItemEntity(
            id = "v1", kind = "VIDEO", title = "Video", description = "Desc", imageUrl = null, publishedAt = 20L,
            sourceId = "src-2", sourceName = "Chan", url = "https://www.youtube.com/watch?v=v1", author = null,
            videoId = "v1", channelName = "Channel", duration = "12:00", savedAt = 99L,
        )
        every { newsDao.observeSaved() } returns flowOf(listOf(entity))

        repository.observeSaved().test {
            val saved = awaitItem().single()
            assertEquals(savedVideo, saved.item)
            assertEquals(99L, saved.savedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given saved ids when observeSavedIds then they are exposed as a set`() = runTest {
        every { newsDao.observeSavedIds() } returns flowOf(listOf("a1", "v1"))

        repository.observeSavedIds().test {
            assertEquals(setOf("a1", "v1"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given unsave then the snapshot is deleted by id`() = runTest {
        repository.unsave("a1")

        coVerify(exactly = 1) { newsDao.deleteSaved("a1") }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 17 — one-time legacy filter → follow migration
    // ══════════════════════════════════════════════════════════════════════════

    private fun source(id: String, language: String, enabled: Boolean = true) =
        articleSource(id = id, enabled = enabled).copy(language = language, isDefault = true)

    private fun givenMigrationPending(legacy: LegacyNewsFilters, sources: List<ContentSourceEntity>) {
        coEvery { userPrefsDataStore.isNewsFollowMigrationDone() } returns false
        coEvery { userPrefsDataStore.readLegacyNewsFilters() } returns legacy
        coEvery { newsDao.getAllSources() } returns sources
        coEvery { newsDao.getEnabledSources() } returns emptyList()
    }

    @Test
    fun `given legacy English only when refreshAll then Spanish and German sources are unfollowed once`() = runTest {
        givenMigrationPending(
            LegacyNewsFilters(languages = setOf("en"), sourceIds = null, explicitEmpty = false),
            listOf(source("en1", "en"), source("es1", "es"), source("de1", "de")),
        )

        repository.refreshAll()

        coVerify(exactly = 1) { newsDao.setSourceEnabled("es1", false) }
        coVerify(exactly = 1) { newsDao.setSourceEnabled("de1", false) }
        coVerify(exactly = 0) { newsDao.setSourceEnabled("en1", any()) }
        coVerify(exactly = 1) { userPrefsDataStore.completeNewsFollowMigration() }
    }

    @Test
    fun `given no persisted legacy languages when migrating then the old English-only default applies`() = runTest {
        givenMigrationPending(
            LegacyNewsFilters(languages = null, sourceIds = null, explicitEmpty = false),
            listOf(source("en1", "en"), source("es1", "es")),
        )

        repository.refreshAll()

        coVerify(exactly = 1) { newsDao.setSourceEnabled("es1", false) }
    }

    @Test
    fun `given a legacy allowlist when migrating then only allowlisted sources in a selected language stay followed`() = runTest {
        givenMigrationPending(
            LegacyNewsFilters(languages = setOf("en", "es"), sourceIds = setOf("es1"), explicitEmpty = false),
            listOf(source("en1", "en"), source("es1", "es")),
        )

        repository.refreshAll()

        coVerify(exactly = 1) { newsDao.setSourceEnabled("en1", false) }
        coVerify(exactly = 0) { newsDao.setSourceEnabled("es1", any()) }
    }

    @Test
    fun `given a fresh install with no sources when migrating then only the flag is set`() = runTest {
        givenMigrationPending(LegacyNewsFilters(null, null, false), emptyList())

        repository.refreshAll()

        coVerify(exactly = 0) { newsDao.setSourceEnabled(any(), any()) }
        coVerify(exactly = 0) { userPrefsDataStore.readLegacyNewsFilters() }
        coVerify(exactly = 1) { userPrefsDataStore.completeNewsFollowMigration() }
    }

    @Test
    fun `given the migration fails when refreshAll then the refresh still succeeds and the flag stays unset`() = runTest {
        givenMigrationPending(LegacyNewsFilters(null, null, false), listOf(source("en1", "en")))
        coEvery { userPrefsDataStore.readLegacyNewsFilters() } throws RuntimeException("disk")

        val result = repository.refreshAll()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { userPrefsDataStore.completeNewsFollowMigration() }
    }

    @Test
    fun `given the migration is already done when observing sources then legacy filters are never read`() = runTest {
        every { newsDao.observeSources() } returns flowOf(emptyList())

        repository.observeSources().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { userPrefsDataStore.readLegacyNewsFilters() }
    }

    @Test
    fun `given a pending migration when observing sources then it runs before the first emission`() = runTest {
        givenMigrationPending(LegacyNewsFilters(setOf("en"), null, false), listOf(source("en1", "en"), source("es1", "es")))
        every { newsDao.observeSources() } returns flowOf(emptyList())

        repository.observeSources().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { newsDao.setSourceEnabled("es1", false) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 18 — default follow policy + site URLs
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a Spanish device when seeding defaults then English and Spanish defaults start followed but not German`() = runTest {
        deviceLanguage = "es"
        val slot = slot<List<ContentSourceEntity>>()
        coEvery { newsDao.insertSourcesIfAbsent(capture(slot)) } returns Unit
        coEvery { newsDao.getEnabledSources() } returns emptyList()

        repository.refreshAll()

        val byLanguage = slot.captured.groupBy { it.language }
        assertTrue(byLanguage.getValue("en").all { it.isEnabled })
        assertTrue(byLanguage.getValue("es").all { it.isEnabled })
        assertTrue(byLanguage.getValue("de").none { it.isEnabled })
    }

    @Test
    fun `given every default in the catalog then each has an https site url`() {
        assertTrue(DefaultSources.all.all { it.siteUrl?.startsWith("https://") == true })
    }

    @Test
    fun `given a persisted default without a site url when refreshAll then the catalog site url is filled in`() = runTest {
        val catalog = DefaultSources.all.first { it.id == "default_article_scg" }
        coEvery { newsDao.getAllSources() } returns listOf(catalog.copy(siteUrl = null, isEnabled = false))
        coEvery { newsDao.getEnabledSources() } returns emptyList()

        repository.refreshAll()

        coVerify(exactly = 1) {
            newsDao.updateSource(match { it.siteUrl == catalog.siteUrl && !it.isEnabled && it.feedUrl == catalog.feedUrl })
        }
    }

    private val rssWithChannelLink = """
        <rss version="2.0"><channel><title>Blog</title><link>https://custom.example.com/</link>
        <item><title>A</title><link>https://custom.example.com/a</link></item></channel></rss>
    """.trimIndent()

    @Test
    fun `given a custom article source fetched with a new channel link then its site url is persisted`() = runTest {
        val custom = articleSource(id = "custom_1")
        coEvery { newsDao.getEnabledSources() } returns listOf(custom)
        coEvery { feedService.fetchFeed(any(), any(), any()) } returns fetched(body = rssWithChannelLink)

        repository.refreshAll()

        coVerify(exactly = 1) { newsDao.updateSiteUrl("custom_1", "https://custom.example.com/") }
    }

    @Test
    fun `given a default article source when fetched then the feed link never overrides the catalog site url`() = runTest {
        val default = articleSource(id = "default_x").copy(isDefault = true)
        coEvery { newsDao.getEnabledSources() } returns listOf(default)
        coEvery { feedService.fetchFeed(any(), any(), any()) } returns fetched(body = rssWithChannelLink)

        repository.refreshAll()

        coVerify(exactly = 0) { newsDao.updateSiteUrl(any(), any()) }
    }

    @Test
    fun `given the persisted site url already matches when fetched then nothing is written`() = runTest {
        val custom = articleSource(id = "custom_1").copy(siteUrl = "https://custom.example.com/")
        coEvery { newsDao.getEnabledSources() } returns listOf(custom)
        coEvery { feedService.fetchFeed(any(), any(), any()) } returns fetched(body = rssWithChannelLink)

        repository.refreshAll()

        coVerify(exactly = 0) { newsDao.updateSiteUrl(any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 19 — resolveSource
    // ══════════════════════════════════════════════════════════════════════════

    private val channelId = "UC8ZGymAvfP97qJabgqUkz4A"
    private val youTubeFeedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"

    private val spanishRss = """
        <?xml version="1.0"?><rss version="2.0"><channel><title>Blog de Magic</title>
        <link>https://blog.example.com</link><language>es-ES</language>
        <item><title>A</title><link>https://blog.example.com/a</link></item></channel></rss>
    """.trimIndent()

    private val youTubeAtom = """
        <feed xmlns="http://www.w3.org/2005/Atom"><title>Magic: The Gathering</title>
        <link rel="alternate" href="https://www.youtube.com/channel/$channelId"/><entry><title>V</title></entry></feed>
    """.trimIndent()

    private fun page(body: String, finalUrl: String) = Result.success(FetchedPage(body, finalUrl, "text/html"))

    private fun articleEntity(id: String, publishedAt: Long) =
        NewsArticleEntity(id, "Title $id", "D", null, publishedAt, "Blog de Magic", "preview", "https://blog.example.com/$id", null)

    private fun resolveError(result: Result<*>): SourceResolveError? =
        (result.exceptionOrNull() as? SourceResolveException)?.error

    @Test
    fun `given a direct feed url then name language site and a newest-first preview of three come from the feed`() = runTest {
        coEvery { feedService.fetchPage("https://blog.example.com/feed") } returns page(spanishRss, "https://blog.example.com/feed/")
        every { rssParser.parse(spanishRss, any(), "Blog de Magic") } returns
            listOf(articleEntity("1", 1L), articleEntity("2", 4L), articleEntity("3", 3L), articleEntity("4", 2L))

        val resolved = repository.resolveSource("blog.example.com/feed").getOrThrow()

        assertEquals("Blog de Magic", resolved.name)
        assertEquals("https://blog.example.com/feed/", resolved.feedUrl)
        assertEquals("https://blog.example.com", resolved.siteUrl)
        assertEquals("es", resolved.language)
        assertEquals(SourceType.ARTICLE, resolved.type)
        assertEquals(listOf("2", "3", "4"), resolved.preview.map { it.id })
    }

    @Test
    fun `given a website with an autodiscovery link then its feed is fetched`() = runTest {
        val html = """<html><head><link rel="alternate" type="application/rss+xml" href="/rss.xml"></head></html>"""
        coEvery { feedService.fetchPage("https://blog.example.com") } returns page(html, "https://blog.example.com/")
        coEvery { feedService.fetchPage("https://blog.example.com/rss.xml") } returns page(spanishRss, "https://blog.example.com/rss.xml")
        every { rssParser.parse(any(), any(), any()) } returns listOf(articleEntity("1", 1L))

        val resolved = repository.resolveSource("http://blog.example.com").getOrThrow()

        assertEquals("https://blog.example.com/rss.xml", resolved.feedUrl)
    }

    @Test
    fun `given a website without autodiscovery then fallback paths are probed until one is a feed`() = runTest {
        coEvery { feedService.fetchPage(any()) } returns page("<html></html>", "https://blog.example.com/")
        coEvery { feedService.fetchPage("https://blog.example.com/feed/") } returns page(spanishRss, "https://blog.example.com/feed/")
        every { rssParser.parse(any(), any(), any()) } returns listOf(articleEntity("1", 1L))

        val resolved = repository.resolveSource("https://blog.example.com").getOrThrow()

        assertEquals("https://blog.example.com/feed/", resolved.feedUrl)
        coVerify(exactly = 0) { feedService.fetchPage("https://blog.example.com/rss") }
    }

    @Test
    fun `given a website with no feed anywhere then NO_FEED_FOUND after at most one page and six probes`() = runTest {
        coEvery { feedService.fetchPage(any()) } returns page("<html></html>", "https://blog.example.com/")

        val result = repository.resolveSource("https://blog.example.com")

        assertEquals(SourceResolveError.NO_FEED_FOUND, resolveError(result))
        coVerify(exactly = 7) { feedService.fetchPage(any()) }
    }

    @Test
    fun `given a YouTube handle then the channel page is fetched once and its feed resolved as a video source`() = runTest {
        val html = """<link rel="canonical" href="https://www.youtube.com/channel/$channelId">"""
        coEvery { feedService.fetchPage("https://www.youtube.com/@magic") } returns page(html, "https://www.youtube.com/@magic")
        coEvery { feedService.fetchPage(youTubeFeedUrl) } returns page(youTubeAtom, youTubeFeedUrl)
        every { ytParser.parse(youTubeAtom, any(), any()) } returns
            listOf(NewsVideoEntity("v1", "V", "D", null, 1L, "Magic: The Gathering", "preview", "https://y/v1", "Magic"))

        val resolved = repository.resolveSource("@magic").getOrThrow()

        assertEquals(SourceType.VIDEO, resolved.type)
        assertEquals(youTubeFeedUrl, resolved.feedUrl)
        assertEquals("https://www.youtube.com/channel/$channelId", resolved.siteUrl)
        assertEquals("Magic: The Gathering", resolved.name)
        coVerify(exactly = 1) { feedService.fetchPage("https://www.youtube.com/@magic") }
    }

    @Test
    fun `given a YouTube handle page that 404s then YOUTUBE_CHANNEL_NOT_FOUND`() = runTest {
        coEvery { feedService.fetchPage(any()) } returns
            Result.failure(PageFetchException(PageFetchException.Reason.HTTP_ERROR, httpCode = 404))

        assertEquals(SourceResolveError.YOUTUBE_CHANNEL_NOT_FOUND, resolveError(repository.resolveSource("@ghost")))
    }

    @Test
    fun `given a YouTube page without a channel id then YOUTUBE_CHANNEL_NOT_FOUND`() = runTest {
        coEvery { feedService.fetchPage(any()) } returns page("<html>consent</html>", "https://consent.youtube.com")

        assertEquals(SourceResolveError.YOUTUBE_CHANNEL_NOT_FOUND, resolveError(repository.resolveSource("@magic")))
    }

    @Test
    fun `given a channel the user already follows then ALREADY_FOLLOWING without any network call`() = runTest {
        coEvery { newsDao.getAllSources() } returns listOf(
            articleSource(id = "default_video_mtg_official").copy(feedUrl = youTubeFeedUrl, type = "VIDEO", isEnabled = true),
        )

        val result = repository.resolveSource(channelId)

        assertEquals(SourceResolveError.ALREADY_FOLLOWING, resolveError(result))
        coVerify(exactly = 0) { feedService.fetchPage(any()) }
    }

    @Test
    fun `given a feed with no items then EMPTY_FEED`() = runTest {
        coEvery { feedService.fetchPage(any()) } returns page(spanishRss, "https://blog.example.com/feed")
        every { rssParser.parse(any(), any(), any()) } returns emptyList()

        assertEquals(SourceResolveError.EMPTY_FEED, resolveError(repository.resolveSource("https://blog.example.com/feed")))
    }

    @Test
    fun `given a redirect to plain http then NOT_HTTPS`() = runTest {
        coEvery { feedService.fetchPage(any()) } returns Result.failure(PageFetchException(PageFetchException.Reason.NOT_HTTPS))

        assertEquals(SourceResolveError.NOT_HTTPS, resolveError(repository.resolveSource("https://blog.example.com")))
    }

    @Test
    fun `given a network failure then UNREACHABLE and nothing is reported to Crashlytics`() = runTest {
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
        coEvery { feedService.fetchPage(any()) } returns Result.failure(java.net.UnknownHostException("blog.example.com"))

        assertEquals(SourceResolveError.UNREACHABLE, resolveError(repository.resolveSource("https://blog.example.com")))
        verify(exactly = 0) { crashlytics.recordException(any()) }
    }

    @Test
    fun `given an unexpected failure then UNREACHABLE and the non-fatal carries no url`() = runTest {
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
        val recorded = slot<Throwable>()
        every { crashlytics.recordException(capture(recorded)) } returns Unit
        coEvery { feedService.fetchPage(any()) } returns
            Result.failure(IllegalStateException("boom for https://secret.example.com/feed"))

        val result = repository.resolveSource("https://secret.example.com/feed")

        assertEquals(SourceResolveError.UNREACHABLE, resolveError(result))
        var cause: Throwable? = recorded.captured
        while (cause != null) {
            assertFalse(cause.message.orEmpty().contains("secret.example.com"))
            cause = cause.cause
        }
    }

    @Test
    fun `given text that is not a link then INVALID_INPUT without any network call`() = runTest {
        assertEquals(SourceResolveError.INVALID_INPUT, resolveError(repository.resolveSource("magic news")))
        coVerify(exactly = 0) { feedService.fetchPage(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 20 — followResolvedSource
    // ══════════════════════════════════════════════════════════════════════════

    private val resolvedBlog = ResolvedSource(
        name = "Blog de Magic",
        feedUrl = "https://blog.example.com/feed/",
        siteUrl = "https://blog.example.com",
        type = SourceType.ARTICLE,
        language = "es",
        preview = emptyList(),
    )

    @Test
    fun `given a new feed when following then a followed custom source is inserted and refreshed right away`() = runTest {
        val inserted = slot<List<ContentSourceEntity>>()
        coEvery { newsDao.insertSourcesIfAbsent(capture(inserted)) } returns Unit
        coEvery { newsDao.getSourceById(any()) } answers { inserted.captured.single() }
        coEvery { feedService.fetchFeed(any(), any(), any()) } returns fetched()

        val followed = repository.followResolvedSource(resolvedBlog, name = "My Blog", language = "es").getOrThrow()

        with(inserted.captured.single()) {
            assertTrue(id.startsWith("custom_"))
            assertEquals("My Blog", name)
            assertEquals("https://blog.example.com/feed/", feedUrl)
            assertEquals("ARTICLE", type)
            assertEquals("es", language)
            assertEquals("https://blog.example.com", siteUrl)
            assertTrue(isEnabled)
            assertFalse(isDefault)
        }
        assertEquals("My Blog", followed.name)
        coVerify(exactly = 1) { feedService.fetchFeed("https://blog.example.com/feed/", any(), any()) }
    }

    @Test
    fun `given the feed matches an unfollowed default when following then the default is followed instead of duplicated`() = runTest {
        val default = articleSource(id = "default_blog", enabled = false).copy(isDefault = true, feedUrl = "https://www.blog.example.com/feed")
        coEvery { newsDao.getAllSources() } returns listOf(default)

        val followed = repository.followResolvedSource(resolvedBlog, name = "ignored", language = "es").getOrThrow()

        assertEquals("default_blog", followed.id)
        coVerify(exactly = 1) { newsDao.setSourceEnabled("default_blog", true) }
        coVerify(exactly = 0) { newsDao.insertSourcesIfAbsent(any()) }
    }

    @Test
    fun `given the feed is already followed when following then ALREADY_FOLLOWING`() = runTest {
        coEvery { newsDao.getAllSources() } returns listOf(articleSource(id = "custom_1").copy(feedUrl = "https://blog.example.com/feed"))

        val result = repository.followResolvedSource(resolvedBlog, name = "x", language = "en")

        assertEquals(SourceResolveError.ALREADY_FOLLOWING, resolveError(result))
    }

    @Test
    fun `given a non https feed when following then NOT_HTTPS and nothing is persisted`() = runTest {
        val result = repository.followResolvedSource(resolvedBlog.copy(feedUrl = "http://blog.example.com/feed"), "x", "en")

        assertEquals(SourceResolveError.NOT_HTTPS, resolveError(result))
        coVerify(exactly = 0) { newsDao.insertSourcesIfAbsent(any()) }
    }
}
