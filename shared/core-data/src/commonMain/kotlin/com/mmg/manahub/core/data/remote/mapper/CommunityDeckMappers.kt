package com.mmg.manahub.core.data.remote.mapper

import com.mmg.manahub.core.data.remote.dto.ArchidektDeckDetailDto
import com.mmg.manahub.core.data.remote.dto.ArchidektDeckSummaryDto
import com.mmg.manahub.core.data.remote.dto.ArchidektOracleCardDto
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
            val cardDto = entry.card!!
            val oracle = cardDto.oracleCard!!
            CommunityDeckCard(
                name = oracle.name,
                quantity = entry.quantity,
                categories = entry.categories ?: emptyList(),
                oracleId = oracle.uid,
                // `cardDto.uid` is the Scryfall PRINTING uuid (distinct from `oracle.uid`,
                // the oracle-card uuid shared across printings) — verified live 2026-07-13.
                scryfallId = cardDto.uid,
                manaCost = oracle.manaCost,
                typeLine = buildTypeLine(oracle),
                cmc = oracle.cmc,
                rarity = cardDto.rarity,
                setCode = cardDto.edition?.editioncode ?: "",
                setName = cardDto.edition?.editionname ?: "",
                priceUsd = cardDto.prices?.tcg,
                priceEur = cardDto.prices?.cm,
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
        featuredImageUrl = customFeatured.takeIf { it.isNotBlank() } ?: featured.takeIf { it.isNotBlank() },
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
    colorIdentity = colors.filterValues { it > 0 }.keys.map { colorKey ->
        when (colorKey.lowercase()) {
            "white" -> "W"
            "blue" -> "U"
            "black" -> "B"
            "red" -> "R"
            "green" -> "G"
            else -> colorKey.uppercase() // Fallback to raw if already a letter
        }
    },
    // A user-picked `customFeatured` wins over the automatic `featured` crop when present.
    featuredImageUrl = customFeatured.takeIf { it.isNotBlank() } ?: featured.takeIf { it.isNotBlank() },
)

/** Maps a paged Archidekt search response to the domain [CommunityDeckSearchResult]. */
fun ArchidektSearchResultDto.toDomain(): CommunityDeckSearchResult = CommunityDeckSearchResult(
    totalCount = count,
    hasMore = next != null,
    decks = results.map { it.toDomain() },
)

/**
 * Composes the canonical MTG type line ("Supertype Type — Subtype") from Archidekt's oracle
 * card fields. The em-dash + subtype segment is only appended when [ArchidektOracleCardDto.subTypes]
 * is non-empty (e.g. instants/sorceries have no subtypes to show).
 */
private fun buildTypeLine(oracle: ArchidektOracleCardDto): String {
    val mainPart = (oracle.superTypes + oracle.types).joinToString(" ")
    return if (oracle.subTypes.isNotEmpty()) {
        "$mainPart — ${oracle.subTypes.joinToString(" ")}"
    } else {
        mainPart
    }
}
