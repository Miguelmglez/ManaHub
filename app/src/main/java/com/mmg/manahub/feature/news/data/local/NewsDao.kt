package com.mmg.manahub.feature.news.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsDao {

    // ── Articles ─────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArticles(articles: List<NewsArticleEntity>)

    @Query("SELECT * FROM news_articles ORDER BY published_at DESC")
    fun observeArticles(): Flow<List<NewsArticleEntity>>

    @Query("SELECT MIN(fetched_at) FROM news_articles")
    suspend fun oldestArticleFetchedAt(): Long?

    @Query("DELETE FROM news_articles WHERE fetched_at < :before")
    suspend fun evictArticlesBefore(before: Long)

    // ── Videos ───────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideos(videos: List<NewsVideoEntity>)

    @Query("SELECT * FROM news_videos ORDER BY published_at DESC")
    fun observeVideos(): Flow<List<NewsVideoEntity>>

    @Query("SELECT MIN(fetched_at) FROM news_videos")
    suspend fun oldestVideoFetchedAt(): Long?

    @Query("DELETE FROM news_videos WHERE fetched_at < :before")
    suspend fun evictVideosBefore(before: Long)

    // ── Content sources ──────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSourcesIfAbsent(sources: List<ContentSourceEntity>)

    @Update
    suspend fun updateSource(source: ContentSourceEntity)

    @Delete
    suspend fun deleteSource(source: ContentSourceEntity)

    @Query("SELECT * FROM content_sources ORDER BY type, name")
    fun observeSources(): Flow<List<ContentSourceEntity>>

    @Query("SELECT * FROM content_sources WHERE is_enabled = 1")
    suspend fun getEnabledSources(): List<ContentSourceEntity>

    @Query("SELECT * FROM content_sources")
    suspend fun getAllSources(): List<ContentSourceEntity>
}
