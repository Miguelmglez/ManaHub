package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.feature.news.domain.model.NewsItem
import com.mmg.manahub.feature.news.domain.repository.NewsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetNewsFeedUseCase @Inject constructor(
    private val repository: NewsRepository,
) {
    operator fun invoke(): Flow<List<NewsItem>> = repository.observeNews()
}
