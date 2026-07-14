package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.domain.repository.NewsRepository
import kotlinx.coroutines.flow.Flow

class ManageSourcesUseCase(
    private val repository: NewsRepository,
) {
    fun observeSources(): Flow<List<ContentSource>> = repository.observeSources()

    suspend fun toggleSource(sourceId: String, enabled: Boolean) =
        repository.toggleSource(sourceId, enabled)

    suspend fun addCustomSource(name: String, feedUrl: String, type: SourceType): Result<ContentSource> =
        repository.addCustomSource(name, feedUrl, type)

    suspend fun deleteSource(sourceId: String) = repository.deleteSource(sourceId)

    suspend fun validateFeed(feedUrl: String, type: SourceType): Result<Int> =
        repository.validateFeed(feedUrl, type)
}
