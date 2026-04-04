package com.mmg.magicfolder.feature.news.domain.usecase

import com.mmg.magicfolder.feature.news.domain.model.NewsItem
import com.mmg.magicfolder.feature.news.domain.repository.NewsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetNewsFeedUseCase @Inject constructor(
    private val repository: NewsRepository,
) {
    operator fun invoke(): Flow<List<NewsItem>> = repository.observeNews()
}
