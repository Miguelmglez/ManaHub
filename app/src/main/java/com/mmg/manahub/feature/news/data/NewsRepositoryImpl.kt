package com.mmg.manahub.feature.news.data

import android.util.Log
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.NewsDao
import com.mmg.manahub.core.data.local.entity.ContentSourceEntity
import com.mmg.manahub.core.data.local.entity.NewsArticleEntity
import com.mmg.manahub.core.data.local.entity.NewsSavedItemEntity
import com.mmg.manahub.core.data.local.entity.NewsVideoEntity
import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.RefreshResult
import com.mmg.manahub.core.model.news.ResolvedSource
import com.mmg.manahub.core.model.news.SavedNewsItem
import com.mmg.manahub.core.model.news.SourceResolveError
import com.mmg.manahub.core.model.news.SourceResolveException
import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.core.util.recordSafeNonFatal
import com.mmg.manahub.feature.news.data.local.DefaultSources
import com.mmg.manahub.feature.news.data.parser.RssFeedParser
import com.mmg.manahub.feature.news.data.parser.YouTubeRssFeedParser
import com.mmg.manahub.feature.news.data.remote.FeedFetchResult
import com.mmg.manahub.feature.news.data.remote.NewsFeedService
import com.mmg.manahub.feature.news.data.remote.PageFetchException
import com.mmg.manahub.feature.news.domain.source.DefaultFollowPolicy
import com.mmg.manahub.feature.news.domain.source.FeedAutodiscovery
import com.mmg.manahub.feature.news.domain.source.FeedDocument
import com.mmg.manahub.feature.news.domain.source.FeedIdentity
import com.mmg.manahub.feature.news.domain.source.LegacyFollowMigration
import com.mmg.manahub.feature.news.domain.source.SiteUrlDerivation
import com.mmg.manahub.feature.news.domain.source.SourceInput
import com.mmg.manahub.feature.news.domain.source.SourceInputClassifier
import com.mmg.manahub.feature.news.domain.source.SourceUrl
import com.mmg.manahub.feature.news.domain.source.YouTubeChannelIdExtractor
import com.mmg.manahub.feature.news.domain.source.YouTubeUrls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.util.Locale
import java.util.UUID

/**
 * KMP migration — Hilt→Koin cutover batch 3. Plain class (no `@Inject`/`@Singleton`); built as a
 * native Koin `single` in [com.mmg.manahub.feature.news.di.newsKoinModule].
 * Never log or report a feed/page URL (custom sources are user input).
 */
class NewsRepositoryImpl(
    private val newsDao: NewsDao,
    private val feedService: NewsFeedService,
    private val rssParser: RssFeedParser,
    private val ytParser: YouTubeRssFeedParser,
    private val userPrefsDataStore: UserPreferencesDataStore,
    private val deviceLanguage: () -> String = { Locale.getDefault().language },
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : NewsRepository {

    // Both streams wait for the one-time follow migration so a previously hidden language never flashes in.
    override fun observeNews(): Flow<List<NewsItem>> =
        combine(newsDao.observeArticles(), newsDao.observeVideos()) { articles, videos ->
            val articleItems = articles.map { it.toDomain() }
            val videoItems = videos.map { it.toDomain() }
            (articleItems + videoItems).sortedByDescending { it.publishedAt }
        }.onStart { ensureFollowMigration() }

    override fun observeSources(): Flow<List<ContentSource>> =
        newsDao.observeSources()
            .onStart { ensureFollowMigration() }
            .map { sources -> sources.map { it.toDomain() } }

    override fun observeSaved(): Flow<List<SavedNewsItem>> =
        newsDao.observeSaved().map { saved -> saved.map { it.toDomain() } }

    override fun observeSavedIds(): Flow<Set<String>> =
        newsDao.observeSavedIds().map { it.toSet() }

    override suspend fun save(item: NewsItem) {
        newsDao.insertSaved(item.toSavedEntity(savedAt = nowMs()))
    }

    override suspend fun unsave(itemId: String) {
        newsDao.deleteSaved(itemId)
    }

    override suspend fun refreshAll(force: Boolean): Result<RefreshResult> = try {
        ensureFollowMigration()

        // IGNORE keeps existing rows (and the user's follow choice); only brand-new defaults get the policy.
        val deviceLanguage = deviceLanguage()
        newsDao.insertSourcesIfAbsent(
            DefaultSources.all.map {
                it.copy(isEnabled = DefaultFollowPolicy.isFollowedByDefault(it.language, deviceLanguage))
            }
        )

        // Reconcile previously-seeded default sources against the current DefaultSources catalog
        // (see KDoc on reconcileDefaultSources for why insertSourcesIfAbsent's IGNORE conflict
        // strategy alone is not enough to propagate a feedUrl fix/removal to existing installs).
        reconcileDefaultSources()

        // Evict old cache (>7 days) — unconditional, independent of per-source staleness.
        val evictBefore = nowMs() - EVICT_MS
        newsDao.evictArticlesBefore(evictBefore)
        newsDao.evictVideosBefore(evictBefore)

        // Per-source staleness: a source is stale when it has never been fetched
        // (lastFetchedAt == 0) or its watermark is older than FRESH_MS. `force` (manual
        // pull-to-refresh) bypasses this gate and re-contacts every enabled source — but
        // conditional GET (If-None-Match/If-Modified-Since) still applies, so an unchanged
        // feed still short-circuits to a cheap 304.
        val now = nowMs()
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
    } catch (e: CancellationException) {
        throw e
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
    } catch (e: CancellationException) {
        throw e
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

    private val followMigrationMutex = Mutex()

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
        val now = nowMs()
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
                        persistChannelSiteUrl(source, fetchResult.body)
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
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.w(TAG, "Failed to fetch source ${source.id}: ${e::class.simpleName}")
        recordSafeNonFatal("news_source_fetch:${source.id}", e)
        SourceFetchOutcome.FAILED
    }

    // Defaults take their site from the catalog (reconcileDefaultSources); only custom sources learn it from the feed.
    private suspend fun persistChannelSiteUrl(source: ContentSourceEntity, body: String) {
        if (source.isDefault) return
        val link = SiteUrlDerivation.httpsLink(FeedDocument.parseChannelMeta(body)?.link) ?: return
        if (link != source.siteUrl) newsDao.updateSiteUrl(source.id, link)
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
     *  - a persisted default source whose `siteUrl` differs from the catalog gets the catalog value.
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
            var updated = source
            if (source.feedUrl != catalogEntry.feedUrl) {
                updated = updated.copy(
                    feedUrl = catalogEntry.feedUrl,
                    etag = null,
                    lastModified = null,
                    lastFetchedAt = 0L,
                )
            }
            if (source.siteUrl != catalogEntry.siteUrl) {
                updated = updated.copy(siteUrl = catalogEntry.siteUrl)
            }
            if (updated != source) newsDao.updateSource(updated)
        }

        val idsToDelete = persistedDefaults.map { it.id }.filter { it in RETIRED_DEFAULT_SOURCE_IDS }
        if (idsToDelete.isNotEmpty()) {
            newsDao.deleteSourcesByIds(idsToDelete)
        }
    }

    // Absent legacy language key = the old English-only default; a fresh install has no sources yet, so only the flag is set.
    private suspend fun ensureFollowMigration() {
        try {
            if (userPrefsDataStore.isNewsFollowMigrationDone()) return
            followMigrationMutex.withLock {
                if (userPrefsDataStore.isNewsFollowMigrationDone()) return
                val sources = newsDao.getAllSources()
                if (sources.isNotEmpty()) {
                    val legacy = userPrefsDataStore.readLegacyNewsFilters()
                    val followedIds = LegacyFollowMigration.computeFollowedIds(
                        sources = sources.map { it.toDomain() },
                        legacyLanguages = legacy.languages ?: LEGACY_DEFAULT_LANGUAGES,
                        legacyAllowlist = legacy.sourceIds,
                        legacyExplicitEmpty = legacy.explicitEmpty,
                    )
                    sources
                        .filter { it.isEnabled != (it.id in followedIds) }
                        .forEach { newsDao.setSourceEnabled(it.id, it.id in followedIds) }
                }
                userPrefsDataStore.completeNewsFollowMigration()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordSafeNonFatal("news_follow_migration", e)
        }
    }

    override suspend fun setSourceFollowed(sourceId: String, followed: Boolean) {
        newsDao.setSourceEnabled(sourceId, followed)
    }

    override suspend fun deleteSource(sourceId: String) {
        val source = newsDao.getSourceById(sourceId) ?: return
        if (!source.isDefault) newsDao.deleteSource(source)
    }

    // ── Source resolution ("paste any URL") ──────────────────────────────────

    override suspend fun resolveSource(input: String): Result<ResolvedSource> = try {
        Result.success(resolve(input))
    } catch (e: CancellationException) {
        throw e
    } catch (e: SourceResolveException) {
        Result.failure(e)
    } catch (e: IOException) {
        Result.failure(SourceResolveException(SourceResolveError.UNREACHABLE))
    } catch (e: Exception) {
        recordSafeNonFatal("news_source_resolve", e.withoutMessage())
        Result.failure(SourceResolveException(SourceResolveError.UNREACHABLE))
    }

    private suspend fun resolve(input: String): ResolvedSource {
        val candidate = when (val classified = SourceInputClassifier.classify(input).getOrThrow()) {
            is SourceInput.YouTubeFeed -> youTubeFeed(classified.channelId)
            is SourceInput.YouTubeChannelId -> youTubeFeed(classified.channelId)
            is SourceInput.YouTubeHandle -> {
                val page = feedService.fetchPage(classified.pageUrl)
                    .getOrElse { throw it.toResolveException(youTubePage = true) }
                val channelId = YouTubeChannelIdExtractor.extract(page.body)
                    ?: throw SourceResolveException(SourceResolveError.YOUTUBE_CHANNEL_NOT_FOUND)
                youTubeFeed(channelId)
            }
            is SourceInput.Web -> webFeed(classified.url)
        }
        return candidate.toResolvedSource()
    }

    private suspend fun youTubeFeed(channelId: String): FeedCandidate {
        val feedUrl = YouTubeUrls.feedUrl(channelId)
        failIfAlreadyFollowing(feedUrl)
        val page = feedService.fetchPage(feedUrl).getOrElse { throw it.toResolveException(youTubePage = true) }
        if (!FeedDocument.looksLikeFeed(page.body)) throw SourceResolveException(SourceResolveError.YOUTUBE_CHANNEL_NOT_FOUND)
        return FeedCandidate(feedUrl, page.body)
    }

    /** The page itself when it is a feed; else its autodiscovered feeds; else the common feed paths. */
    private suspend fun webFeed(url: String): FeedCandidate {
        val page = feedService.fetchPage(url).getOrElse { throw it.toResolveException(youTubePage = false) }
        if (FeedDocument.looksLikeFeed(page.body)) return FeedCandidate(page.finalUrl, page.body)
        val discovered = FeedAutodiscovery.discover(page.body, page.finalUrl).take(MAX_DISCOVERED_CANDIDATES)
        for (feedUrl in (discovered + FeedAutodiscovery.fallbackFeedUrls(page.finalUrl)).distinct()) {
            val feed = feedService.fetchPage(feedUrl).getOrNull() ?: continue
            if (FeedDocument.looksLikeFeed(feed.body)) return FeedCandidate(feed.finalUrl, feed.body)
        }
        throw SourceResolveException(SourceResolveError.NO_FEED_FOUND)
    }

    private suspend fun FeedCandidate.toResolvedSource(): ResolvedSource {
        failIfAlreadyFollowing(feedUrl)
        val type = if (SourceUrl.parse(feedUrl)?.host?.let(YouTubeUrls::isYouTubeHost) == true) SourceType.VIDEO else SourceType.ARTICLE
        val meta = FeedDocument.parseChannelMeta(body)
        val name = meta?.title?.take(MAX_NAME_LENGTH)
            ?: SourceUrl.parse(feedUrl)?.host?.removePrefix("www.")
            ?: feedUrl
        val items = try {
            when (type) {
                SourceType.ARTICLE -> rssParser.parse(body, PREVIEW_SOURCE_ID, name).map { it.toDomain() }
                SourceType.VIDEO -> ytParser.parse(body, PREVIEW_SOURCE_ID, name).map { it.toDomain() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Malformed third-party XML is not an app bug, so it is not reported.
            throw SourceResolveException(SourceResolveError.NO_FEED_FOUND)
        }
        if (items.isEmpty()) throw SourceResolveException(SourceResolveError.EMPTY_FEED)
        return ResolvedSource(
            name = name,
            feedUrl = feedUrl,
            siteUrl = SiteUrlDerivation.derive(feedUrl, meta?.link),
            type = type,
            language = meta?.language,
            preview = items.sortedByDescending { it.publishedAt }.take(PREVIEW_SIZE),
        )
    }

    private suspend fun failIfAlreadyFollowing(feedUrl: String) {
        if (findByFeedIdentity(feedUrl)?.isEnabled == true) {
            throw SourceResolveException(SourceResolveError.ALREADY_FOLLOWING)
        }
    }

    private suspend fun findByFeedIdentity(feedUrl: String): ContentSourceEntity? {
        val identity = FeedIdentity.of(feedUrl)
        return newsDao.getAllSources().firstOrNull { FeedIdentity.of(it.feedUrl) == identity }
    }

    private fun Throwable.toResolveException(youTubePage: Boolean): Throwable = when {
        this is PageFetchException && reason == PageFetchException.Reason.NOT_HTTPS ->
            SourceResolveException(SourceResolveError.NOT_HTTPS)
        this is PageFetchException && youTubePage && httpCode == HTTP_NOT_FOUND ->
            SourceResolveException(SourceResolveError.YOUTUBE_CHANNEL_NOT_FOUND)
        this is IOException -> SourceResolveException(SourceResolveError.UNREACHABLE)
        else -> this
    }

    override suspend fun followResolvedSource(
        source: ResolvedSource,
        name: String,
        language: String,
    ): Result<ContentSource> = try {
        if (SourceUrl.parse(source.feedUrl)?.scheme != "https") {
            throw SourceResolveException(SourceResolveError.NOT_HTTPS)
        }
        val existing = findByFeedIdentity(source.feedUrl)
        val followed = when {
            existing == null -> ContentSourceEntity(
                id = "custom_${UUID.randomUUID()}",
                name = name,
                feedUrl = source.feedUrl,
                type = source.type.name,
                isEnabled = true,
                isDefault = false,
                language = language,
                siteUrl = source.siteUrl,
            ).also { newsDao.insertSourcesIfAbsent(listOf(it)) }
            existing.isEnabled -> throw SourceResolveException(SourceResolveError.ALREADY_FOLLOWING)
            else -> existing.copy(isEnabled = true).also { newsDao.setSourceEnabled(it.id, true) }
        }
        // F9: fetch right away so the new source shows items immediately; a failure is retried on the next refresh.
        refreshSource(followed.id)
        Result.success(followed.toDomain())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private data class FeedCandidate(val feedUrl: String, val body: String)

    companion object {
        private const val TAG = "NewsRepository"
        private const val FRESH_MS = 60 * 60 * 1000L          // 1 hour per-source watermark TTL
        private const val EVICT_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
        private const val MAX_CONCURRENT_FETCHES = 6
        private const val MAX_DISCOVERED_CANDIDATES = 3
        private const val PREVIEW_SIZE = 3
        private const val MAX_NAME_LENGTH = 100
        private const val HTTP_NOT_FOUND = 404
        private const val PREVIEW_SOURCE_ID = "preview"

        /** What the retired filter showed when its language key was never persisted. */
        private val LEGACY_DEFAULT_LANGUAGES = setOf("en")

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

// Keeps the stack trace but drops the message, which can carry a user-entered URL or host.
private fun Throwable.withoutMessage(): Throwable =
    RuntimeException(this::class.simpleName).also { it.stackTrace = stackTrace }

// ── Entity ↔ Domain mappers ─────────────────────────────────────────────────

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
    id            = id,
    name          = name,
    feedUrl       = feedUrl,
    type          = if (type == "VIDEO") SourceType.VIDEO else SourceType.ARTICLE,
    isEnabled     = isEnabled,
    isDefault     = isDefault,
    iconUrl       = iconUrl,
    language      = language,
    siteUrl       = siteUrl,
    lastFetchedAt = lastFetchedAt,
)

private fun NewsItem.toSavedEntity(savedAt: Long) = NewsSavedItemEntity(
    id          = id,
    kind        = if (this is NewsItem.Video) KIND_VIDEO else KIND_ARTICLE,
    title       = title,
    description = description,
    imageUrl    = imageUrl,
    publishedAt = publishedAt,
    sourceId    = sourceId,
    sourceName  = sourceName,
    url         = url,
    author      = (this as? NewsItem.Article)?.author,
    videoId     = (this as? NewsItem.Video)?.videoId,
    channelName = (this as? NewsItem.Video)?.channelName,
    duration    = (this as? NewsItem.Video)?.duration,
    savedAt     = savedAt,
)

private fun NewsSavedItemEntity.toDomain(): SavedNewsItem {
    val item = if (kind == KIND_VIDEO) {
        NewsItem.Video(
            id          = id,
            title       = title,
            description = description,
            imageUrl    = imageUrl,
            publishedAt = publishedAt,
            sourceName  = sourceName,
            sourceId    = sourceId,
            url         = url,
            videoId     = videoId ?: id,
            channelName = channelName ?: sourceName,
            duration    = duration,
        )
    } else {
        NewsItem.Article(
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
    }
    return SavedNewsItem(item = item, savedAt = savedAt)
}

private const val KIND_ARTICLE = "ARTICLE"
private const val KIND_VIDEO = "VIDEO"
