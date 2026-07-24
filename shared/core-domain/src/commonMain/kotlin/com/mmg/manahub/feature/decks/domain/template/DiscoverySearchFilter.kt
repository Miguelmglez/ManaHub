package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.model.Card

/**
 * Deck Engine Unification plan D7 (Phase 4.2) -- pure, client-side filtering over an
 * already-computed [DeckDiscoveryV2] list. No network, no recomputation of the clusters
 * themselves: [DiscoverSynergiesV2UseCase] stays the sole clustering engine, this only narrows
 * its output for display.
 *
 * Both filters, when active, are ANDed together (a text query AND a card-name pick both narrow
 * the SAME result set) -- there is no "OR" mode; a user combining both expects both to matter.
 */
object DiscoverySearchFilter {

    /**
     * @param query free text matched against [DeckDiscoveryV2.label], case-insensitive,
     *   substring match. Blank (after trim) -- no filtering by label.
     * @param selectedCardNames zero or more owned card names (as picked from the collection) --
     *   a cluster survives only if AT LEAST ONE of its [DeckDiscoveryV2.members] matches one of
     *   these names (case-insensitive). Empty -- no filtering by card.
     */
    fun apply(
        discoveries: List<DeckDiscoveryV2>,
        query: String,
        selectedCardNames: Set<String>,
    ): List<DeckDiscoveryV2> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty() && selectedCardNames.isEmpty()) return discoveries

        val lowerNames = selectedCardNames.map { it.lowercase() }.toSet()
        return discoveries.filter { discovery ->
            val matchesQuery = trimmedQuery.isEmpty() || discovery.label.contains(trimmedQuery, ignoreCase = true)
            val matchesCards = lowerNames.isEmpty() || discovery.members.any { it.name.lowercase() in lowerNames }
            matchesQuery && matchesCards
        }
    }

    /**
     * Every distinct card name appearing across [discoveries]' members, sorted alphabetically --
     * the pickable pool for a "search by card" chooser (only cards that actually appear in at
     * least one cluster are worth offering; picking any other owned card would always yield zero
     * results).
     */
    fun pickableCardNames(discoveries: List<DeckDiscoveryV2>): List<String> =
        discoveries.flatMap { it.members }.map { it.name }.distinct().sorted()

    /**
     * Every distinct member [Card] across [discoveries] that matches the SAME [query]/
     * [selectedCardNames] filter [apply] uses for clusters (ANDed, same semantics) — this is the
     * flat "matching cards" preview shown directly under the search bar, so the search query
     * narrows both WHICH strategy clusters show (via [apply]) and WHICH individual cards show in
     * that preview, instead of the query only ever affecting the cluster list. Blank query and
     * empty [selectedCardNames] -- empty result (the preview is search-triggered only, never a
     * full unfiltered card dump).
     */
    fun matchingCards(
        discoveries: List<DeckDiscoveryV2>,
        query: String,
        selectedCardNames: Set<String>,
    ): List<Card> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty() && selectedCardNames.isEmpty()) return emptyList()

        val lowerNames = selectedCardNames.map { it.lowercase() }.toSet()
        return discoveries.flatMap { it.members }
            .distinctBy { it.scryfallId }
            .filter { card ->
                val matchesQuery = trimmedQuery.isEmpty() || card.name.contains(trimmedQuery, ignoreCase = true)
                val matchesCards = lowerNames.isEmpty() || card.name.lowercase() in lowerNames
                matchesQuery && matchesCards
            }
    }
}
