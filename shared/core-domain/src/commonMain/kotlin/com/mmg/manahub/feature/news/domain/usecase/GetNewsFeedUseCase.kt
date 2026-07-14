package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.domain.repository.NewsRepository
import kotlinx.coroutines.flow.Flow

class GetNewsFeedUseCase(
    private val repository: NewsRepository,
) {
    operator fun invoke(): Flow<List<NewsItem>> = repository.observeNews()
}
