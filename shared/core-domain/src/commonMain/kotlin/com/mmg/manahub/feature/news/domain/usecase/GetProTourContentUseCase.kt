package com.mmg.manahub.feature.news.domain.usecase

import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.core.model.news.NewsItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Filters the already-cached News feed down to Pro Tour / high-level competitive event coverage
 * (Competitive feature, Phase 5). A PURE in-memory keyword predicate over
 * [NewsRepository.observeNews] — zero new Room table, network call, or Worker: the News feature
 * already fetches/caches articles + videos from its RSS/YouTube sources, so this use case simply
 * re-slices that existing stream. Any UI wired to [GetNewsFeedUseCase]/[NewsRepository.observeNews]
 * (e.g. `NewsViewModel`, Home's `MTG_NEWS` widget) keeps working unchanged — this is purely an
 * additive read, never a mutation of the underlying news cache.
 *
 * Matches (case-insensitive) against [NewsItem.title] + [NewsItem.description] against:
 * `"Pro Tour"`, `"PT <SET>"` (a 3-letter set-code abbreviation, e.g. "PT DFT"),
 * `"Regional Championship"`, `"World Championship"`, `"Arena Championship"`, `"Qualifier"`.
 *
 * The bare `"Metagame"` keyword was DROPPED (Competitive redesign, 2026-08): it matched almost any
 * generic deck-tech/metagame-report article, not just actual Pro Tour coverage, making this filter
 * feel broken to users who expected only Pro-Tour-related results.
 */
class GetProTourContentUseCase(
    private val repository: NewsRepository,
) {
    operator fun invoke(): Flow<List<NewsItem>> =
        repository.observeNews().map { items -> items.filter { it.matchesProTourKeywords() } }

    private fun NewsItem.matchesProTourKeywords(): Boolean =
        PRO_TOUR_KEYWORD_REGEX.containsMatchIn("$title $description")

    private companion object {
        /**
         * The whole alternation is case-insensitive per the Competitive feature spec — including
         * the `PT [A-Z]{3}` branch, so "pt dft" also matches even though a real set-code
         * abbreviation is conventionally uppercase. No bare `"Metagame"` branch — see the class
         * KDoc for why it was dropped.
         */
        val PRO_TOUR_KEYWORD_REGEX = Regex(
            "Pro Tour|PT [A-Z]{3}|Regional Championship|World Championship|Arena Championship|Qualifier",
            RegexOption.IGNORE_CASE,
        )
    }
}
