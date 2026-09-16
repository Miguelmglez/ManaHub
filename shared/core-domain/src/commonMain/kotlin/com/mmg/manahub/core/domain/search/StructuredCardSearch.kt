package com.mmg.manahub.core.domain.search
// COMMENTS_REVIEWED: 2026-09-16

import com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.SearchCriterion

// Turns one structured query into both a Scryfall fragment (remote tab) and a local matcher
// (Collection tab), so a caller never re-derives that pairing by hand.
object StructuredCardSearch {

    fun scryfallFragment(
        query: AdvancedSearchQuery,
        buildScryfallQueryUseCase: BuildScryfallQueryUseCase = BuildScryfallQueryUseCase(),
    ): String? = buildScryfallQueryUseCase(query).takeIf { it.isNotBlank() }

    fun matches(card: Card, query: AdvancedSearchQuery?, lenient: Boolean = true): Boolean =
        query == null || query.isEmpty() || AdvancedSearchCardMatcher.matches(card, query, lenient = lenient)

    // Excludes a CardFunction criterion from local matching -- it resolves through
    // CardFunctionOption's own vocabulary, which can disagree with categoryTagKeys (see ADR-009).
    fun matchesForCategoryBrowse(card: Card, query: AdvancedSearchQuery?, categoryTagKeys: Set<String>): Boolean {
        if (categoryTagKeys.isEmpty()) return matches(card, query)
        val tagMatch = (card.tags.map { it.key } + card.userTags.map { it.key }).any { it in categoryTagKeys }
        // No query active: the tag is the sole gate (matches(card, null) is trivially true).
        if (query == null || query.isEmpty()) return tagMatch
        val hasCardFunctionCriterion = query.criteria.any { it is SearchCriterion.CardFunction }
        if (!hasCardFunctionCriterion) return matches(card, query) || tagMatch
        val structural = query.criteria.filterNot { it is SearchCriterion.CardFunction }
        val structuralMatch = structural.isEmpty() ||
            AdvancedSearchCardMatcher.matches(card, AdvancedSearchQuery(structural), lenient = true)
        return structuralMatch && tagMatch
    }

    fun collectionMatches(
        cards: List<Card>,
        query: AdvancedSearchQuery?,
        nameFilter: String = "",
        lenient: Boolean = true,
    ): List<Card> = cards.filter { card ->
        matches(card, query, lenient) && (nameFilter.isBlank() || card.name.contains(nameFilter, ignoreCase = true))
    }
}
