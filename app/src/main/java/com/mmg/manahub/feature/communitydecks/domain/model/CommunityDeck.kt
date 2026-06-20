package com.mmg.manahub.feature.communitydecks.domain.model

/**
 * Domain representation of a community deck fetched from Archidekt.
 *
 * This is the clean, Android-free model the presentation layer consumes; the raw
 * Archidekt DTOs never leave the data layer (they are mapped here in
 * `CommunityDecksMappers`).
 *
 * @property format the ManaHub format string (already mapped from Archidekt's
 *   numeric `deckFormat` via [ArchidektFormat]).
 * @property sourceUrl the canonical Archidekt deck URL, used for attribution on import.
 */
data class CommunityDeck(
    val archidektId: Int,
    val name: String,
    val description: String,
    val format: String,
    val owner: CommunityDeckOwner,
    val viewCount: Int,
    val createdAt: String,
    val updatedAt: String,
    val cards: List<CommunityDeckCard>,
    val sourceUrl: String,
)
