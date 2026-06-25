package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.domain.repository.NewsRepository

class RefreshNewsFeedUseCase(
    private val repository: NewsRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repository.refreshAll()
}
