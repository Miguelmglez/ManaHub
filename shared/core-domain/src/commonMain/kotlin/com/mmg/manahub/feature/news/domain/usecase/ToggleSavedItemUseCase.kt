package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.core.model.news.NewsItem

class ToggleSavedItemUseCase(
    private val repository: NewsRepository,
) {
    /** Saves [item] when it is not saved yet, otherwise removes it; returns the new saved state. */
    suspend operator fun invoke(item: NewsItem, isCurrentlySaved: Boolean): Boolean {
        if (isCurrentlySaved) repository.unsave(item.id) else repository.save(item)
        return !isCurrentlySaved
    }
}
