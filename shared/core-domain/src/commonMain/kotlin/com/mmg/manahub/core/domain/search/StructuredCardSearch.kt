package com.mmg.manahub.core.domain.search
// COMMENTS_REVIEWED: 2026-09-16

import com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card

// Turns one structured query into both a Scryfall fragment (remote tab) and a local matcher
// (Collection tab), so a caller never re-derives that pairing by hand.
object StructuredCardSearch {

    fun scryfallFragment(
        query: AdvancedSearchQuery,
        buildScryfallQueryUseCase: BuildScryfallQueryUseCase = BuildScryfallQueryUseCase(),
    ): String? = buildScryfallQueryUseCase(query).takeIf { it.isNotBlank() }

    fun matches(card: Card, query: AdvancedSearchQuery?, lenient: Boolean = true): Boolean =
        query == null || query.isEmpty() || AdvancedSearchCardMatcher.matches(card, query, lenient = lenient)

    fun collectionMatches(
        cards: List<Card>,
        query: AdvancedSearchQuery?,
        nameFilter: String = "",
        lenient: Boolean = true,
    ): List<Card> = cards.filter { card ->
        matches(card, query, lenient) && (nameFilter.isBlank() || card.name.contains(nameFilter, ignoreCase = true))
    }
}
