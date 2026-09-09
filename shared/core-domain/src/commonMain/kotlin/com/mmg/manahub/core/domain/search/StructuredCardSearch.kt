package com.mmg.manahub.core.domain.search

import com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card

/**
 * The single commonMain place that turns one structured [AdvancedSearchQuery] into BOTH halves of
 * a "search both tabs" flow: a Scryfall fragment for the remote/All-cards tab, and a local
 * [AdvancedSearchCardMatcher] filter for the Collection tab.
 *
 * Deck Wizard Commander v3 plan, Phase 3.3 — extracted from
 * [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel.applyStructuredSearch]/
 * `collectionCardsMatching` (Deck Studio's Analysis-tab "Browse for X" flow) so the Deck Wizard's
 * commander pick step, its second caller, never re-derives this pairing by hand — "one
 * applyStructuredSearch -> Scryfall + local matcher" everywhere a structured query drives two
 * tabs, per the campaign's own no-duplicated-search-logic rule. Deck Studio's own additional
 * AND-ed local filters (a collection-tag filter, a plain name filter) stay caller-side — they are
 * a different key space per feature, not part of this shared contract.
 */
object StructuredCardSearch {

    /**
     * The Scryfall query fragment for [query], or `null` when it renders to a blank string (an
     * empty query, or one whose criteria are all locally-only). [buildScryfallQueryUseCase]
     * defaults to a fresh instance — the class has zero dependencies, mirroring every existing
     * call site's own fallback convention.
     */
    fun scryfallFragment(
        query: AdvancedSearchQuery,
        buildScryfallQueryUseCase: BuildScryfallQueryUseCase = BuildScryfallQueryUseCase(),
    ): String? = buildScryfallQueryUseCase(query).takeIf { it.isNotBlank() }

    /** True when [card] satisfies [query] — a `null` or empty [query] always matches, mirroring
     * [AdvancedSearchCardMatcher.matches]'s own empty-criteria contract. */
    fun matches(card: Card, query: AdvancedSearchQuery?, lenient: Boolean = true): Boolean =
        query == null || query.isEmpty() || AdvancedSearchCardMatcher.matches(card, query, lenient = lenient)

    /** [cards] filtered by [query] (locally, lenient by default so a Scryfall-only criterion never
     * renders a falsely-empty Collection tab) AND an optional plain-text [nameFilter]. */
    fun collectionMatches(
        cards: List<Card>,
        query: AdvancedSearchQuery?,
        nameFilter: String = "",
        lenient: Boolean = true,
    ): List<Card> = cards.filter { card ->
        matches(card, query, lenient) && (nameFilter.isBlank() || card.name.contains(nameFilter, ignoreCase = true))
    }
}
