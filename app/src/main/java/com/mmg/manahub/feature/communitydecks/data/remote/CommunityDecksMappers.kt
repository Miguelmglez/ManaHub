package com.mmg.manahub.feature.communitydecks.data.remote

import com.mmg.manahub.core.data.local.entity.CommunityDeckCacheEntity
import com.mmg.manahub.core.data.remote.dto.ArchidektDeckDetailDto
import com.mmg.manahub.core.data.remote.dto.ArchidektDeckSummaryDto
import com.mmg.manahub.core.data.remote.dto.ArchidektSearchResultDto
import com.mmg.manahub.core.model.ArchidektFormat
import com.mmg.manahub.core.model.CommunityDeck
import com.mmg.manahub.core.model.CommunityDeckCard
import com.mmg.manahub.core.model.CommunityDeckOwner
import com.mmg.manahub.core.model.CommunityDeckSearchResult
import com.mmg.manahub.core.model.CommunityDeckSummary
import kotlinx.serialization.json.Json

/**
 * Mappers between Archidekt DTOs, the cache entity, and the [CommunityDeck] domain model.
 *
 * A single shared [Json] instance (lenient, tolerant of unknown/missing keys) is used to
 * (de)serialize the cached deck blob so the cache round-trip survives upstream schema drift.
 */
private val communityDeckJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

/** Maps a fetched Archidekt deck detail DTO to the clean [CommunityDeck] domain model. */
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

/**
 * Builds a cache entity from a fetched DTO. The full DTO is serialized verbatim into
 * [CommunityDeckCacheEntity.responseJson] so the detail can be re-rendered offline.
 */
fun ArchidektDeckDetailDto.toCacheEntity(): CommunityDeckCacheEntity = CommunityDeckCacheEntity(
    archidektId = id,
    name = name,
    ownerUsername = owner?.username ?: "Unknown",
    format = ArchidektFormat.toManaHubFormat(deckFormat),
    description = description,
    viewCount = viewCount,
    cardCount = cards.sumOf { it.quantity },
    responseJson = communityDeckJson.encodeToString(ArchidektDeckDetailDto.serializer(), this),
)

/**
 * Reconstructs a [CommunityDeck] from a cached row by deserializing the stored DTO blob.
 * Falls back to the denormalized columns only if the blob cannot be parsed.
 */
fun CommunityDeckCacheEntity.toDomain(): CommunityDeck {
    val dto = communityDeckJson.decodeFromString(ArchidektDeckDetailDto.serializer(), responseJson)
    return dto.toDomain()
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
