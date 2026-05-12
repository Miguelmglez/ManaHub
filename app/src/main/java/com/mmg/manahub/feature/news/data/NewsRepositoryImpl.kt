package com.mmg.manahub.feature.news.data

import android.util.Log
import com.mmg.manahub.feature.news.data.local.ContentSourceEntity
import com.mmg.manahub.feature.news.data.local.DefaultSources
import com.mmg.manahub.feature.news.data.local.NewsArticleEntity
import com.mmg.manahub.feature.news.data.local.NewsDao
import com.mmg.manahub.feature.news.data.local.NewsVideoEntity
import com.mmg.manahub.feature.news.data.parser.RssFeedParser
import com.mmg.manahub.feature.news.data.parser.YouTubeRssFeedParser
import com.mmg.manahub.feature.news.data.remote.NewsFeedService
import com.mmg.manahub.feature.news.domain.model.ContentSource
import com.mmg.manahub.feature.news.domain.model.NewsItem
import com.mmg.manahub.feature.news.domain.model.SourceType
import com.mmg.manahub.feature.news.domain.repository.NewsRepository
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

        // Check if cache is fresh
        val articleAge = newsDao.oldestArticleFetchedAt()
        val videoAge = newsDao.oldestVideoFetchedAt()
        val now = System.currentTimeMillis()
        val isFresh = articleAge != null && (now - articleAge) < FRESH_MS
                && videoAge != null && (now - videoAge) < FRESH_MS

        if (!isFresh) {
            fetchAllFeeds()
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "refreshAll failed", e)
        Result.failure(e)
    }

    private suspend fun fetchAllFeeds() = coroutineScope {
        val sources = newsDao.getEnabledSources()
        val results = sources.map { source ->
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
                    Log.w(TAG, "Failed to fetch ${source.name}: ${e.message}")
                }
            }
        }
        results.awaitAll()
    }

    override suspend fun toggleSource(sourceId: String, enabled: Boolean) {
        val sources = newsDao.getAllSources()
        val source = sources.find { it.id == sourceId } ?: return
        newsDao.updateSource(source.copy(isEnabled = enabled))
    }

    override suspend fun addCustomSource(
        name: String,
        feedUrl: String,
        type: SourceType,
    ): Result<ContentSource> = try {
        val entity = ContentSourceEntity(
            id = "custom_${UUID.randomUUID()}",
            name = name,
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
