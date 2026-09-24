package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.core.model.news.ContentSource
import kotlinx.coroutines.flow.Flow

class ManageSourcesUseCase(
    private val repository: NewsRepository,
) {
    fun observeSources(): Flow<List<ContentSource>> = repository.observeSources()

    suspend fun setFollowed(sourceId: String, followed: Boolean) = repository.setSourceFollowed(sourceId, followed)

    suspend fun deleteSource(sourceId: String) = repository.deleteSource(sourceId)

    suspend fun refreshSource(sourceId: String): Result<Unit> = repository.refreshSource(sourceId)
}
