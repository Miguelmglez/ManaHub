package com.mmg.manahub.core.model

/**
 * A single popular Commander deck surfaced from Archidekt's public deck-search API
 * (Home dashboard "Trending decks" SOCIAL_HUB slides — Home feature overhaul Phase 1.2.c).
 *
 * Archidekt's `v3` search endpoint exposes no "likes"/"favorites" signal, so popularity is
 * ranked by [viewCount] (all-time view count via `orderBy=-viewCount`), not a true weekly
 * trending delta — UI copy must say "Popular on Archidekt", never "Trending this week".
 *
 * @param id Archidekt's numeric deck id.
 * @param name Deck title as entered by its author.
 * @param viewCount All-time view count on Archidekt.
 * @param deckUrl Deep link to the deck's Archidekt page (`https://archidekt.com/decks/{id}`),
 *   opened in the system browser. Displaying this data requires visible "via Archidekt"
 *   attribution and a link back per Archidekt's integrator guidelines.
 */
data class ArchidektTrendingDeck(
    val id: Int,
    val name: String,
    val viewCount: Int,
    val deckUrl: String,
)
