package com.mmg.manahub.core.model

/**
 * Lightweight domain model for a community deck shown in search/browse results.
 *
 * Unlike [CommunityDeck], this carries no card list — it is built from the paged
 * Archidekt search summary so the list screen stays cheap. The full card list is
 * loaded only when the user opens a deck's detail.
 *
 * @property format the ManaHub format string (already mapped from Archidekt's
 *   numeric `deckFormat` via [ArchidektFormat]).
 * @property colorIdentity the deck's color identity, derived from the search DTO's
 *   `colors` map keys (e.g. `["W", "U"]`).
 */
data class CommunityDeckSummary(
    val archidektId: Int,
    val name: String,
    val size: Int,
    val format: String,
    val owner: CommunityDeckOwner,
    val viewCount: Int,
    val createdAt: String,
    val updatedAt: String,
    val colorIdentity: List<String>,
)
