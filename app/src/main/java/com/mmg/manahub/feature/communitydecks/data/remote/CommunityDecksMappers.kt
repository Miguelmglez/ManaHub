package com.mmg.manahub.feature.communitydecks.data.remote

import com.mmg.manahub.core.data.local.entity.CommunityDeckCacheEntity
import com.mmg.manahub.core.data.remote.dto.ArchidektDeckDetailDto
import com.mmg.manahub.core.data.remote.mapper.toDomain
import com.mmg.manahub.core.model.ArchidektFormat
import com.mmg.manahub.core.model.CommunityDeck
import com.mmg.manahub.core.util.recordNonFatal
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
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
 *
 * Returns `null` when [responseJson] cannot be parsed (corrupt/malformed blob — e.g. schema drift
 * or a truncated write) rather than throwing. This function is NOT on the production `getById`
 * read path today (see [com.mmg.manahub.feature.communitydecks.data.CommunityDeckCacheImpl], which
 * decodes [ArchidektDeckDetailDto] directly and applies the same null-on-corrupt guard), but is
 * kept null-safe for its own round-trip coverage (`CommunityDecksMappersTest`) and any future
 * caller.
 */
fun CommunityDeckCacheEntity.toDomain(): CommunityDeck? = try {
    val dto = communityDeckJson.decodeFromString(ArchidektDeckDetailDto.serializer(), responseJson)
    dto.toDomain()
} catch (e: CancellationException) {
    throw e
} catch (e: SerializationException) {
    recordNonFatal("community_deck_cache_corrupt", e)
    null
} catch (e: IllegalArgumentException) {
    recordNonFatal("community_deck_cache_corrupt", e)
    null
}
