package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.RefreshResult
import com.mmg.manahub.core.model.news.SourceType
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for fetching and managing news/content feeds.
 * Moved from `:app` feature/news/domain/repository during the KMP migration.
 */
interface NewsRepository {
    fun observeNews(): Flow<List<NewsItem>>
    fun observeSources(): Flow<List<ContentSource>>

    /**
     * Refreshes every stale enabled source (per-source watermark, ~1h TTL), or every enabled
     * source when [force] is true (pull-to-refresh). `force` still respects conditional GET
     * (If-None-Match/If-Modified-Since are always sent) — it only bypasses the TTL gate, not the
     * 304 short-circuit.
     */
    suspend fun refreshAll(force: Boolean = false): Result<RefreshResult>

    /** Refreshes a single source immediately, bypassing the staleness check (F9 — new source). */
    suspend fun refreshSource(sourceId: String): Result<Unit>

    suspend fun toggleSource(sourceId: String, enabled: Boolean)
    suspend fun addCustomSource(
        name: String,
        feedUrl: String,
        type: SourceType,
        language: String = "en",
    ): Result<ContentSource>
    suspend fun deleteSource(sourceId: String)
    suspend fun validateFeed(feedUrl: String, type: SourceType): Result<Int>

    /**
     * Best-effort detection of the feed's channel-level language (RSS 2.0 `<channel><language>`),
     * used to pre-select the add-source form's language chip. Returns null on failure, a feed
     * format with no such element, or an unmapped language code — never throws.
     */
    suspend fun detectFeedLanguage(feedUrl: String): String?
}
