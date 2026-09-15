package com.mmg.manahub.core.domain.search

import com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.SearchCriterion

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

    /**
     * Deck Wizard Commander v5, X0 (H2/S2): true when [card] matches [query] for a section's
     * "Browse for &lt;Category&gt;" Collection filter, where [categoryTagKeys] is that category's
     * [com.mmg.manahub.feature.decks.domain.engine.CategoryVocabulary]-sourced CardTag-key set
     * (empty when the category has no tag equivalent, e.g. curve/mana/tribe ids).
     *
     * [query]'s own category criterion is EITHER a [SearchCriterion.CardFunction] (~21 roles,
     * `SectionSearchQuery.DIRECT_ORACLE_TAGS`) or a production-text criterion (`CardType`/
     * `OracleTerms`/`ManaCost`/`ManaProduction`, everything else) -- never both for the same
     * section id. Only the FIRST shape is vulnerable to H2: [SearchCriterion.CardFunction] resolves
     * locally through [com.mmg.manahub.core.model.CardFunctionOption.collectionTagKeys], a
     * DIFFERENT (sometimes wider) vocabulary than the one the analysis classifier reads. So:
     * - When [query] carries a `CardFunction` criterion, that criterion is EXCLUDED from local
     *   evaluation and category membership is decided by [categoryTagKeys] ALONE, ANDed with every
     *   other criterion (color identity, format legality) that remains -- exactly H2's fix
     *   ("Counters Payoff" Browse no longer finds `plus_counters`-only cards the analysis ignores).
     * - Otherwise (a production-text category criterion, already derived from the SAME
     *   `TagDictionary` rule the classifier itself is built from) the ORIGINAL "match if either the
     *   full query OR the tag is present" tolerance is kept UNCHANGED (Deck Wizard v4 G14's own
     *   fix: an untagged-but-structurally-matching card — the tagging engine hasn't caught up yet —
     *   must still surface).
     *
     * When [categoryTagKeys] is empty, behavior is unchanged: the full [query] decides alone.
     */
    fun matchesForCategoryBrowse(card: Card, query: AdvancedSearchQuery?, categoryTagKeys: Set<String>): Boolean {
        if (categoryTagKeys.isEmpty()) return matches(card, query)
        val tagMatch = (card.tags.map { it.key } + card.userTags.map { it.key }).any { it in categoryTagKeys }
        // No structured query active (a bare tag-only filter): the tag is the SOLE gate, same as
        // before X0 -- `matches(card, null)` trivially returns true and would defeat it otherwise.
        if (query == null || query.isEmpty()) return tagMatch
        val hasCardFunctionCriterion = query.criteria.any { it is SearchCriterion.CardFunction }
        if (!hasCardFunctionCriterion) return matches(card, query) || tagMatch
        val structural = query.criteria.filterNot { it is SearchCriterion.CardFunction }
        val structuralMatch = structural.isEmpty() ||
            AdvancedSearchCardMatcher.matches(card, AdvancedSearchQuery(structural), lenient = true)
        return structuralMatch && tagMatch
    }

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
