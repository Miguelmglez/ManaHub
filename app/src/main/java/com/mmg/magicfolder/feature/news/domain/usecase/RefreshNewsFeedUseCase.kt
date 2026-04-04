package com.mmg.magicfolder.feature.news.domain.usecase

import com.mmg.magicfolder.feature.news.domain.repository.NewsRepository
import javax.inject.Inject

class RefreshNewsFeedUseCase @Inject constructor(
    private val repository: NewsRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repository.refreshAll()
}
