package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.core.model.news.RefreshResult

class RefreshNewsFeedUseCase(
    private val repository: NewsRepository,
) {
    /**
     * @param force when true, refreshes every enabled source regardless of its per-source
     *  staleness watermark (manual pull-to-refresh); conditional GET (If-None-Match/
     *  If-Modified-Since) still applies either way.
     */
    suspend operator fun invoke(force: Boolean = false): Result<RefreshResult> = repository.refreshAll(force)
}
