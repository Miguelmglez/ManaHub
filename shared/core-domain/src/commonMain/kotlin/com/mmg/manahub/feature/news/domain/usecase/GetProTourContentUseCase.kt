package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.core.model.news.NewsItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Pro Tour / high-level competitive coverage for MTG Today › Events: a pure in-memory keyword filter
 * over the cached news ([NewsRepository.observeNews]); no request, table or Worker of its own.
 * Matches (case-insensitive) title + description against "Pro Tour", "PT <SET>", "Regional
 * Championship", "World Championship", "Arena Championship" and "Qualifier". A bare "Metagame"
 * branch was dropped because it matched generic deck-tech articles.
 */
class GetProTourContentUseCase(
    private val repository: NewsRepository,
) {
    operator fun invoke(): Flow<List<NewsItem>> =
        repository.observeNews().map { items -> items.filter { it.matchesProTourKeywords() } }

    private fun NewsItem.matchesProTourKeywords(): Boolean =
        PRO_TOUR_KEYWORD_REGEX.containsMatchIn("$title $description")

    private companion object {
        // Case-insensitive on purpose, including the `PT [A-Z]{3}` branch ("pt dft" matches too).
        val PRO_TOUR_KEYWORD_REGEX = Regex(
            "Pro Tour|PT [A-Z]{3}|Regional Championship|World Championship|Arena Championship|Qualifier",
            RegexOption.IGNORE_CASE,
        )
    }
}
