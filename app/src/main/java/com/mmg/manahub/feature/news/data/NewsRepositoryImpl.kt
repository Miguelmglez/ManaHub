package com.mmg.manahub.feature.news.data

import android.util.Log
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.RefreshResult
import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.core.data.local.entity.ContentSourceEntity
import com.mmg.manahub.feature.news.data.local.DefaultSources
import com.mmg.manahub.core.data.local.entity.NewsArticleEntity
import com.mmg.manahub.core.data.local.dao.NewsDao
import com.mmg.manahub.core.data.local.entity.NewsVideoEntity
import com.mmg.manahub.feature.news.data.parser.RssFeedParser
import com.mmg.manahub.feature.news.data.parser.YouTubeRssFeedParser
import com.mmg.manahub.feature.news.data.remote.FeedFetchResult
import com.mmg.manahub.feature.news.data.remote.NewsFeedService
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.core.util.recordSafeNonFatal
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

/**
 * KMP migration — Hilt→Koin cutover batch 3. Plain class (no `@Inject`/`@Singleton`); built as a
 * native Koin `single` in [com.mmg.manahub.feature.news.di.newsKoinModule].
 */
class NewsRepositoryImpl(
    private val newsDao: NewsDao,
    private val feedService: NewsFeedService,
    private val rssParser: RssFeedParser,
    private val ytParser: YouTubeRssFeedParser,
    private val userPrefsDataStore: UserPreferencesDataStore,
) : NewsRepository {

    override fun observeNews(): Flow<List<NewsItem>> =
        combine(newsDao.observeArticles(), newsDao.observeVideos()) { articles, videos ->
            val articleItems = articles.map { it.toDomain() }
            val videoItems = videos.map { it.toDomain() }
            (articleItems + videoItems).sortedByDescending { it.publishedAt }
        }

    override fun observeSources(): Flow<List<ContentSource>> =
        newsDao.observeSources().map { sources -> sources.map { it.toDomain() } }

    override suspend fun refreshAll(force: Boolean): Result<RefreshResult> = try {
        // Ensure default sources are seeded
        newsDao.insertSourcesIfAbsent(DefaultSources.all)

        // Reconcile previously-seeded default sources against the current DefaultSources catalog
        // (see KDoc on reconcileDefaultSources for why insertSourcesIfAbsent's IGNORE conflict
        // strategy alone is not enough to propagate a feedUrl fix/removal to existing installs).
        reconcileDefaultSources()

        // Evict old cache (>7 days) — unconditional, independent of per-source staleness.
        val evictBefore = System.currentTimeMillis() - EVICT_MS
        newsDao.evictArticlesBefore(evictBefore)
        newsDao.evictVideosBefore(evictBefore)

        // Per-source staleness: a source is stale when it has never been fetched
        // (lastFetchedAt == 0) or its watermark is older than FRESH_MS. `force` (manual
        // pull-to-refresh) bypasses this gate and re-contacts every enabled source — but
        // conditional GET (If-None-Match/If-Modified-Since) still applies, so an unchanged
        // feed still short-circuits to a cheap 304.
        val now = System.currentTimeMillis()
        val enabledSources = newsDao.getEnabledSources()
        val sourcesToFetch = if (force) {
            enabledSources
        } else {
            enabledSources.filter { now - it.lastFetchedAt > FRESH_MS }
        }

        val result = if (sourcesToFetch.isNotEmpty()) {
            fetchAllFeeds(sourcesToFetch)
        } else {
            RefreshResult(fetched = 0, failed = 0, notModified = 0)
        }
        Result.success(result)
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.e(TAG, "refreshAll failed", e)
        Result.failure(e)
    }

    override suspend fun refreshSource(sourceId: String): Result<Unit> = try {
        val source = newsDao.getSourceById(sourceId)
        if (source == null) {
            Result.failure(Exception("Source not found"))
        } else {
            when (fetchSemaphore.withPermit { fetchAndPersistSource(source) }) {
                SourceFetchOutcome.FAILED -> Result.failure(Exception("Failed to refresh source"))
                SourceFetchOutcome.FETCHED, SourceFetchOutcome.NOT_MODIFIED -> Result.success(Unit)
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Shared across [fetchAllFeeds] (batch refresh) and [refreshSource] (F9 — refresh-on-add) so
     * both paths honour the SAME "≤[MAX_CONCURRENT_FETCHES] in flight" cap — two independently
     * gated local semaphores would let a burst of `refreshSource` calls overlapping a
     * `refreshAll(force=true)` exceed the documented invariant.
     */
    private val fetchSemaphore = Semaphore(MAX_CONCURRENT_FETCHES)

    /**
     * Fetches, parses/persists and advances the refresh watermark for every [sources] entry, at
     * most [MAX_CONCURRENT_FETCHES] in flight at once (shared via [fetchSemaphore]). One dead/slow
     * source can never abort the batch — each fetch is isolated inside [fetchAndPersistSource]'s
     * own try/catch.
     */
    private suspend fun fetchAllFeeds(sources: List<ContentSourceEntity>): RefreshResult = coroutineScope {
        val outcomes = sources
            .map { source -> async { fetchSemaphore.withPermit { fetchAndPersistSource(source) } } }
            .awaitAll()
        RefreshResult(
            fetched = outcomes.count { it == SourceFetchOutcome.FETCHED },
            failed = outcomes.count { it == SourceFetchOutcome.FAILED },
            notModified = outcomes.count { it == SourceFetchOutcome.NOT_MODIFIED },
        )
    }

    /**
     * Single-source fetch → parse → upsert → watermark-advance pipeline, shared by
     * [fetchAllFeeds] (batch refresh) and [refreshSource] (F9 — refresh-on-add) so the logic
     * lives in exactly one place.
     *
     * On success (200 or 304) the source's `last_fetched_at`/`etag`/`last_modified` watermark
     * always advances. On failure, the watermark is left untouched so the source is retried on
     * the next refresh cycle, and a Crashlytics non-fatal is recorded keyed by the source **id**
     * — never the feed URL, since custom user feeds must not leak into Crashlytics.
     */
    private suspend fun fetchAndPersistSource(source: ContentSourceEntity): SourceFetchOutcome = try {
        val fetchResult = feedService.fetchFeed(source.feedUrl, source.etag, source.lastModified).getOrThrow()
        val now = System.currentTimeMillis()
        when (fetchResult) {
            is FeedFetchResult.NotModified -> {
                // Unchanged since last fetch — keep the existing etag/last_modified, only the
                // watermark timestamp advances so this source isn't re-attempted until stale again.
                newsDao.updateFetchWatermark(source.id, now, source.etag, source.lastModified)
                SourceFetchOutcome.NOT_MODIFIED
            }
            is FeedFetchResult.Fetched -> {
                when (source.type) {
                    "ARTICLE" -> {
                        val articles = rssParser.parse(fetchResult.body, source.id, source.name)
                        if (articles.isNotEmpty()) newsDao.upsertArticles(articles)
                    }
                    "VIDEO" -> {
                        val videos = ytParser.parse(fetchResult.body, source.id, source.name)
                        if (videos.isNotEmpty()) newsDao.upsertVideos(videos)
                    }
                }
                newsDao.updateFetchWatermark(source.id, now, fetchResult.etag, fetchResult.lastModified)
                SourceFetchOutcome.FETCHED
            }
        }
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.w(TAG, "Failed to fetch source ${source.id}: ${e.message}")
        recordSafeNonFatal("news_source_fetch:${source.id}", e)
        SourceFetchOutcome.FAILED
    }

    /**
     * Reconciles previously-seeded DEFAULT sources against the current [DefaultSources] catalog.
     *
     * [NewsDao.insertSourcesIfAbsent] uses `OnConflictStrategy.IGNORE`: it seeds a default source
     * exactly once, the first time it's ever inserted for that `id`. Editing a default source's
     * `feedUrl` in [DefaultSources] — or removing the entry entirely — has ZERO effect on an
     * install that already seeded the OLD row, because the id is unchanged and the conflicting
     * INSERT is silently ignored forever. This method is the reconciliation step that makes those
     * catalog edits actually reach installs that seeded the old version:
     *  - a persisted default source whose `feedUrl` differs from the CURRENT catalog value for
     *    that same id gets its `feedUrl` rewritten, and its conditional-GET/staleness state reset
     *    (`etag`/`lastModified` → null, `lastFetchedAt` → 0) — the old caching headers and
     *    watermark are meaningless against a different URL/server, and leaving them in place
     *    would make the corrected source read as "fresh" and skip its first real fetch. The
     *    user's `isEnabled` preference is left untouched.
     *  - a persisted default source whose id is in [RETIRED_DEFAULT_SOURCE_IDS] (permanently
     *    dead, removed from [DefaultSources] — see the "Retired" comment block there) is deleted
     *    outright via [NewsDao.deleteSourcesByIds], a targeted-by-id delete that bypasses the
     *    `isDefault` guard in [deleteSource]. That guard exists to protect USER-facing deletes
     *    (a user must never be able to delete a default source from the UI) and must never be
     *    weakened generally — this path only ever acts on a fixed, code-controlled id allowlist,
     *    never a caller-supplied id, so it cannot be used to delete an arbitrary default source.
     *
     * Idempotent and cheap on the common case: once a source is reconciled it never drifts again,
     * so a later call is one local Room read (`getAllSources`) and zero writes. Only ever touches
     * rows where `isDefault == true`; a user-added custom source (`isDefault == false`) is never
     * read, updated, or deleted here.
     */
    private suspend fun reconcileDefaultSources() {
        val currentDefaultsById = DefaultSources.all.associateBy { it.id }
        val persistedDefaults = newsDao.getAllSources().filter { it.isDefault }

        for (source in persistedDefaults) {
            val catalogEntry = currentDefaultsById[source.id] ?: continue
            if (source.feedUrl != catalogEntry.feedUrl) {
                newsDao.updateSource(
                    source.copy(
                        feedUrl = catalogEntry.feedUrl,
                        etag = null,
                        lastModified = null,
                        lastFetchedAt = 0L,
                    )
                )
            }
        }

        val idsToDelete = persistedDefaults.map { it.id }.filter { it in RETIRED_DEFAULT_SOURCE_IDS }
        if (idsToDelete.isNotEmpty()) {
            newsDao.deleteSourcesByIds(idsToDelete)
        }
    }

    override suspend fun toggleSource(sourceId: String, enabled: Boolean) {
        newsDao.setSourceEnabled(sourceId, enabled)
    }

    override suspend fun addCustomSource(
        name: String,
        feedUrl: String,
        type: SourceType,
        language: String,
    ): Result<ContentSource> = try {
        require(feedUrl.startsWith("https://")) { "Feed URL must use HTTPS" }
        val entity = ContentSourceEntity(
            id = "custom_${UUID.randomUUID()}",
            name = name.trim().take(100),
            feedUrl = feedUrl,
            type = type.name,
            isEnabled = true,
            isDefault = false,
            language = language,
        )
        newsDao.insertSourcesIfAbsent(listOf(entity))
        Result.success(entity.toDomain())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun deleteSource(sourceId: String) {
        val sources = newsDao.getAllSources()
        val source = sources.find { it.id == sourceId } ?: return
        if (!source.isDefault) {
            newsDao.deleteSource(source)
            pruneSourceFromFilterAllowlist(sourceId)
        }
    }

    /**
     * F6: after deleting a custom source, drop its id from the persisted [NewsFilterPrefs]
     * allowlist so a stale reference doesn't linger forever (it would otherwise make "select
     * all" never match again in [NewsFilterSheet]). Delegates to
     * [UserPreferencesDataStore.pruneNewsFilterSourceId], which performs the read-modify-write
     * atomically inside a single DataStore `edit{}` transaction — a stale-snapshot
     * read-then-write here would risk clobbering a concurrent filter-apply's language/type
     * change (see that method's KDoc). No-op when the id was never explicitly selected.
     */
    private suspend fun pruneSourceFromFilterAllowlist(sourceId: String) {
        userPrefsDataStore.pruneNewsFilterSourceId(sourceId)
    }

    override suspend fun validateFeed(feedUrl: String, type: SourceType): Result<Int> = try {
        val fetchResult = feedService.fetchFeed(feedUrl).getOrThrow()
        val xml = when (fetchResult) {
            is FeedFetchResult.Fetched -> fetchResult.body
            // No etag/lastModified were sent, so a fresh validation fetch should never 304 —
            // but stay defensive rather than assuming the server always honours that.
            FeedFetchResult.NotModified -> return Result.failure(Exception("No items found in feed"))
        }
        val count = when (type) {
            SourceType.ARTICLE -> rssParser.parse(xml, "validate", "Validate").size
            SourceType.VIDEO -> ytParser.parse(xml, "validate", "Validate").size
        }
        if (count > 0) Result.success(count)
        else Result.failure(Exception("No items found in feed"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Best-effort detection of an RSS 2.0 `<channel><language>` element, used to pre-select the
     * language chip in the add-source form (nice-to-have, never blocks the add flow). Returns
     * null on any failure, a non-RSS feed (YouTube Atom has no such element), or an unmapped
     * language code.
     */
    override suspend fun detectFeedLanguage(feedUrl: String): String? = try {
        val fetchResult = feedService.fetchFeed(feedUrl).getOrNull()
        val xml = (fetchResult as? FeedFetchResult.Fetched)?.body
        xml?.let { rssParser.detectChannelLanguage(it) }
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val TAG = "NewsRepository"
        private const val FRESH_MS = 60 * 60 * 1000L          // 1 hour per-source watermark TTL
        private const val EVICT_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
        private const val MAX_CONCURRENT_FETCHES = 6

        /**
         * Fixed, code-controlled allowlist of default source ids retired because their feed is
         * permanently unfetchable (verified via direct HTTP checks, 2026-07-16 — see the
         * "Retired" comment block in [DefaultSources]). Consumed only by
         * [reconcileDefaultSources], which deletes these ids from any install that seeded them
         * under an older catalog. Deliberately a fixed set, not "any id no longer present in
         * [DefaultSources.all]" — that dynamic check would delete every default source on any
         * install if the catalog were ever empty due to a future bug, which a hardcoded allowlist
         * cannot do. Only extend this set when retiring a SPECIFIC known-dead default source.
         */
        private val RETIRED_DEFAULT_SOURCE_IDS = setOf(
            "default_article_edhrec",
            "default_article_gatheringmagic",
            "default_article_cranial",
        )
    }
}

/** Per-source outcome of [NewsRepositoryImpl.fetchAndPersistSource], aggregated into [RefreshResult]. */
private enum class SourceFetchOutcome { FETCHED, NOT_MODIFIED, FAILED }

// ── Entity → Domain mappers ─────────────────────────────────────────────────

private fun NewsArticleEntity.toDomain() = NewsItem.Article(
    id          = id,
    title       = title,
    description = description,
    imageUrl    = imageUrl,
    publishedAt = publishedAt,
    sourceName  = sourceName,
    sourceId    = sourceId,
    url         = url,
    author      = author,
)

private fun NewsVideoEntity.toDomain() = NewsItem.Video(
    id          = videoId,
    title       = title,
    description = description,
    imageUrl    = imageUrl,
    publishedAt = publishedAt,
    sourceName  = sourceName,
    sourceId    = sourceId,
    url         = url,
    videoId     = videoId,
    channelName = channelName,
    duration    = duration,
)

private fun ContentSourceEntity.toDomain() = ContentSource(
    id        = id,
    name      = name,
    feedUrl   = feedUrl,
    type      = if (type == "VIDEO") SourceType.VIDEO else SourceType.ARTICLE,
    isEnabled = isEnabled,
    isDefault = isDefault,
    iconUrl   = iconUrl,
    language  = language,
)
