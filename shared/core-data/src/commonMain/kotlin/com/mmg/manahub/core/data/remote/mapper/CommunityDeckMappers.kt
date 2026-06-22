package com.mmg.manahub.core.data.remote.mapper

import com.mmg.manahub.core.data.remote.dto.ArchidektDeckDetailDto
import com.mmg.manahub.core.data.remote.dto.ArchidektDeckSummaryDto
import com.mmg.manahub.core.data.remote.dto.ArchidektSearchResultDto
import com.mmg.manahub.core.model.ArchidektFormat
import com.mmg.manahub.core.model.CommunityDeck
import com.mmg.manahub.core.model.CommunityDeckCard
import com.mmg.manahub.core.model.CommunityDeckOwner
import com.mmg.manahub.core.model.CommunityDeckSearchResult
import com.mmg.manahub.core.model.CommunityDeckSummary

/**
 * Maps a fetched Archidekt deck detail DTO to the clean [CommunityDeck] domain model.
 *
 * Only card entries that carry a resolvable oracle card are kept; entries with
 * null [card] or null [oracleCard] are silently filtered out.
 */
fun ArchidektDeckDetailDto.toDomain(): CommunityDeck {
    val mappedCards = cards
        // Keep only entries that carry a resolvable oracle card.
        .filter { it.card?.oracleCard != null }
        .map { entry ->
            val oracle = entry.card!!.oracleCard!!
            CommunityDeckCard(
                name = oracle.name,
                quantity = entry.quantity,
                categories = entry.categories ?: emptyList(),
                oracleId = oracle.uid,
            )
        }

    val mappedOwner = owner
        ?.let { CommunityDeckOwner(id = it.id, username = it.username, avatarUrl = it.avatar) }
        ?: CommunityDeckOwner(id = 0, username = "Unknown", avatarUrl = "")

    return CommunityDeck(
        archidektId = id,
        name = name,
        description = description,
        format = ArchidektFormat.toManaHubFormat(deckFormat),
        owner = mappedOwner,
        viewCount = viewCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
        cards = mappedCards,
        sourceUrl = "https://archidekt.com/decks/$id",
    )
}

/** Maps a single Archidekt search-result entry to the lightweight [CommunityDeckSummary]. */
fun ArchidektDeckSummaryDto.toDomain(): CommunityDeckSummary = CommunityDeckSummary(
    archidektId = id,
    name = name,
    size = size,
    format = ArchidektFormat.toManaHubFormat(deckFormat),
    owner = owner
        ?.let { CommunityDeckOwner(id = it.id, username = it.username, avatarUrl = it.avatar) }
        ?: CommunityDeckOwner(id = 0, username = "Unknown", avatarUrl = ""),
    viewCount = viewCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    colorIdentity = colors.keys.toList(),
)

/** Maps a paged Archidekt search response to the domain [CommunityDeckSearchResult]. */
fun ArchidektSearchResultDto.toDomain(): CommunityDeckSearchResult = CommunityDeckSearchResult(
    totalCount = count,
    hasMore = next != null,
    decks = results.map { it.toDomain() },
)
