package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.core.model.news.SavedNewsItem
import kotlinx.coroutines.flow.Flow

class ObserveSavedItemsUseCase(
    private val repository: NewsRepository,
) {
    operator fun invoke(): Flow<List<SavedNewsItem>> = repository.observeSaved()

    fun savedIds(): Flow<Set<String>> = repository.observeSavedIds()
}
