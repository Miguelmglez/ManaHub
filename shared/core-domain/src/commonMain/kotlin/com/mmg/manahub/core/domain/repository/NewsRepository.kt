package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.SourceType
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for fetching and managing news/content feeds.
 * Moved from `:app` feature/news/domain/repository during the KMP migration.
 */
interface NewsRepository {
    fun observeNews(): Flow<List<NewsItem>>
    fun observeSources(): Flow<List<ContentSource>>
    suspend fun refreshAll(): Result<Unit>
    suspend fun toggleSource(sourceId: String, enabled: Boolean)
    suspend fun addCustomSource(name: String, feedUrl: String, type: SourceType): Result<ContentSource>
    suspend fun deleteSource(sourceId: String)
    suspend fun validateFeed(feedUrl: String, type: SourceType): Result<Int>
}
