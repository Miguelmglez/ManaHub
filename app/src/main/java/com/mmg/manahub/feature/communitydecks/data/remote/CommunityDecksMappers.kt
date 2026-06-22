package com.mmg.manahub.feature.communitydecks.data.remote

import com.mmg.manahub.core.data.local.entity.CommunityDeckCacheEntity
import com.mmg.manahub.core.data.remote.dto.ArchidektDeckDetailDto
import com.mmg.manahub.core.data.remote.mapper.toDomain
import com.mmg.manahub.core.model.ArchidektFormat
import com.mmg.manahub.core.model.CommunityDeck
import kotlinx.serialization.json.Json

/**
 * Android-only mappers between Archidekt DTOs and the Room cache entity.
 *
 * The DTO-to-domain mappers have been moved to the shared `:core-data` module at
 * [com.mmg.manahub.core.data.remote.mapper]; only the entity-related mappers that depend
 * on Room stay here.
 *
 * A single shared [Json] instance (lenient, tolerant of unknown/missing keys) is used to
 * (de)serialize the cached deck blob so the cache round-trip survives upstream schema drift.
 */
private val communityDeckJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
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
