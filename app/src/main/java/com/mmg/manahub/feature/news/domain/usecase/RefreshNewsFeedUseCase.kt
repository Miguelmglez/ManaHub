package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.feature.news.domain.repository.NewsRepository
import javax.inject.Inject

class RefreshNewsFeedUseCase @Inject constructor(
    private val repository: NewsRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repository.refreshAll()
}
