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

    suspend fun addCustomSource(
        name: String,
        feedUrl: String,
        type: SourceType,
        language: String = "en",
    ): Result<ContentSource> = repository.addCustomSource(name, feedUrl, type, language)

    suspend fun deleteSource(sourceId: String) = repository.deleteSource(sourceId)

    suspend fun validateFeed(feedUrl: String, type: SourceType): Result<Int> =
        repository.validateFeed(feedUrl, type)

    /** Refreshes a single source right away (F9 — feedback after adding a new custom source). */
    suspend fun refreshSource(sourceId: String): Result<Unit> = repository.refreshSource(sourceId)

    /** Best-effort channel-language detection to pre-select the add-source form's language chip. */
    suspend fun detectFeedLanguage(feedUrl: String): String? = repository.detectFeedLanguage(feedUrl)
}
