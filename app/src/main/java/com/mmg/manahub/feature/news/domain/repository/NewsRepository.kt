package com.mmg.manahub.feature.news.domain.repository

import com.mmg.manahub.feature.news.domain.model.ContentSource
import com.mmg.manahub.feature.news.domain.model.NewsItem
import com.mmg.manahub.feature.news.domain.model.SourceType
import kotlinx.coroutines.flow.Flow

interface NewsRepository {
    fun observeNews(): Flow<List<NewsItem>>
    fun observeSources(): Flow<List<ContentSource>>
    suspend fun refreshAll(): Result<Unit>
    suspend fun toggleSource(sourceId: String, enabled: Boolean)
    suspend fun addCustomSource(name: String, feedUrl: String, type: SourceType): Result<ContentSource>
    suspend fun deleteSource(sourceId: String)
    suspend fun validateFeed(feedUrl: String, type: SourceType): Result<Int>
}
