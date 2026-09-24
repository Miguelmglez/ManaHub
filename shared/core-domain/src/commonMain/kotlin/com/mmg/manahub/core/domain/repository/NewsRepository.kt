package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.RefreshResult
import com.mmg.manahub.core.model.news.ResolvedSource
import com.mmg.manahub.core.model.news.SavedNewsItem
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for fetching and managing news/content feeds.
 * Moved from `:app` feature/news/domain/repository during the KMP migration.
 */
interface NewsRepository {
    fun observeNews(): Flow<List<NewsItem>>
    fun observeSources(): Flow<List<ContentSource>>

    /** Saved snapshots, newest save first. */
    fun observeSaved(): Flow<List<SavedNewsItem>>
    fun observeSavedIds(): Flow<Set<String>>

    /**
     * Refreshes every stale enabled source (per-source watermark, ~1h TTL), or every enabled
     * source when [force] is true (pull-to-refresh). `force` still respects conditional GET
     * (If-None-Match/If-Modified-Since are always sent) — it only bypasses the TTL gate, not the
     * 304 short-circuit.
     */
    suspend fun refreshAll(force: Boolean = false): Result<RefreshResult>

    /** Refreshes a single source immediately, bypassing the staleness check (F9 — new source). */
    suspend fun refreshSource(sourceId: String): Result<Unit>

    /** Follows or unfollows a source (the `is_enabled` flag); only followed sources feed the Feed. */
    suspend fun setSourceFollowed(sourceId: String, followed: Boolean)

    /** Deletes a custom source; default sources can only be unfollowed. */
    suspend fun deleteSource(sourceId: String)

    suspend fun save(item: NewsItem)
    suspend fun unsave(itemId: String)

    /** Finds a feed behind a website, feed URL, YouTube channel link, `@handle` or channel id. */
    suspend fun resolveSource(input: String): Result<ResolvedSource>

    /** Follows [source] (reusing a matching unfollowed default), then refreshes it right away. */
    suspend fun followResolvedSource(source: ResolvedSource, name: String, language: String): Result<ContentSource>
}
