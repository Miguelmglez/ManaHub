package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.ResolvedSource

class FollowSourceUseCase(
    private val repository: NewsRepository,
) {
    /** A blank [name] falls back to the detected one; an unsupported [language] falls back to English. */
    suspend operator fun invoke(source: ResolvedSource, name: String, language: String): Result<ContentSource> {
        val effectiveName = name.trim().ifEmpty { source.name }.take(MAX_NAME_LENGTH)
        val effectiveLanguage = language.takeIf { it in ContentSource.SUPPORTED_LANGUAGES } ?: "en"
        return repository.followResolvedSource(source, effectiveName, effectiveLanguage)
    }

    private companion object {
        const val MAX_NAME_LENGTH = 100
    }
}
