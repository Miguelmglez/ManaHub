package com.mmg.manahub.feature.news.data

import android.util.Log
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.core.data.local.entity.ContentSourceEntity
import com.mmg.manahub.feature.news.data.local.DefaultSources
import com.mmg.manahub.core.data.local.entity.NewsArticleEntity
import com.mmg.manahub.core.data.local.dao.NewsDao
import com.mmg.manahub.core.data.local.entity.NewsVideoEntity
import com.mmg.manahub.feature.news.data.parser.RssFeedParser
import com.mmg.manahub.feature.news.data.parser.YouTubeRssFeedParser
import com.mmg.manahub.feature.news.data.remote.NewsFeedService
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.domain.repository.NewsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsRepositoryImpl @Inject constructor(
    private val newsDao: NewsDao,
    private val feedService: NewsFeedService,
    private val rssParser: RssFeedParser,
    private val ytParser: YouTubeRssFeedParser,
) : NewsRepository {

    override fun observeNews(): Flow<List<NewsItem>> =
        combine(newsDao.observeArticles(), newsDao.observeVideos()) { articles, videos ->
            val articleItems = articles.map { it.toDomain() }
            val videoItems = videos.map { it.toDomain() }
            (articleItems + videoItems).sortedByDescending { it.publishedAt }
        }

    override fun observeSources(): Flow<List<ContentSource>> =
        newsDao.observeSources().map { sources -> sources.map { it.toDomain() } }

    override suspend fun refreshAll(): Result<Unit> = try {
        // Ensure default sources are seeded
        newsDao.insertSourcesIfAbsent(DefaultSources.all)

        // Evict old cache (>7 days)
        val evictBefore = System.currentTimeMillis() - EVICT_MS
        newsDao.evictArticlesBefore(evictBefore)
        newsDao.evictVideosBefore(evictBefore)

        // Check freshness per table independently — only re-fetch stale data
        val now = System.currentTimeMillis()
        val articleAge = newsDao.oldestArticleFetchedAt()
        val videoAge = newsDao.oldestVideoFetchedAt()
        val articlesStale = articleAge == null || (now - articleAge) >= FRESH_MS
        val videosStale = videoAge == null || (now - videoAge) >= FRESH_MS

        if (articlesStale || videosStale) {
            fetchAllFeeds(fetchArticles = articlesStale, fetchVideos = videosStale)
        }
        Result.success(Unit)
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.e(TAG, "refreshAll failed", e)
        Result.failure(e)
    }

    private suspend fun fetchAllFeeds(fetchArticles: Boolean = true, fetchVideos: Boolean = true) = coroutineScope {
        val sources = newsDao.getEnabledSources()
        val results = sources
            .filter { source ->
                when (source.type) {
                    "ARTICLE" -> fetchArticles
                    "VIDEO" -> fetchVideos
                    else -> false
                }
            }
            .map { source ->
                async {
                    try {
                        val xml = feedService.fetchFeed(source.feedUrl).getOrThrow()
                        when (source.type) {
                            "ARTICLE" -> {
                                val articles = rssParser.parse(xml, source.id, source.name)
                                if (articles.isNotEmpty()) newsDao.upsertArticles(articles)
                            }
                            "VIDEO" -> {
                                val videos = ytParser.parse(xml, source.id, source.name)
                                if (videos.isNotEmpty()) newsDao.upsertVideos(videos)
                            }
                        }
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "Failed to fetch ${source.name}: ${e.message}")
                    }
                }
            }
        results.awaitAll()
    }

    override suspend fun toggleSource(sourceId: String, enabled: Boolean) {
        newsDao.setSourceEnabled(sourceId, enabled)
    }

    override suspend fun addCustomSource(
        name: String,
        feedUrl: String,
        type: SourceType,
    ): Result<ContentSource> = try {
        require(feedUrl.startsWith("https://")) { "Feed URL must use HTTPS" }
        val entity = ContentSourceEntity(
            id = "custom_${UUID.randomUUID()}",
            name = name.trim().take(100),
            feedUrl = feedUrl,
            type = type.name,
            isEnabled = true,
            isDefault = false,
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
        }
    }

    override suspend fun validateFeed(feedUrl: String, type: SourceType): Result<Int> = try {
        val xml = feedService.fetchFeed(feedUrl).getOrThrow()
        val count = when (type) {
            SourceType.ARTICLE -> rssParser.parse(xml, "validate", "Validate").size
            SourceType.VIDEO -> ytParser.parse(xml, "validate", "Validate").size
        }
        if (count > 0) Result.success(count)
        else Result.failure(Exception("No items found in feed"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        private const val TAG = "NewsRepository"
        private const val FRESH_MS = 60 * 60 * 1000L          // 1 hour
        private const val EVICT_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
    }
}

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
