package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.core.model.news.ResolvedSource
import com.mmg.manahub.core.model.news.SourceResolveError
import com.mmg.manahub.core.model.news.SourceResolveException

class ResolveSourceUseCase(
    private val repository: NewsRepository,
) {
    suspend operator fun invoke(input: String): Result<ResolvedSource> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Result.failure(SourceResolveException(SourceResolveError.INVALID_INPUT))
        return repository.resolveSource(trimmed)
    }
}
