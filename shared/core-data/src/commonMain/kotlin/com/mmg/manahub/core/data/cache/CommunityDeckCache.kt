package com.mmg.manahub.core.data.cache

import com.mmg.manahub.core.data.remote.dto.ArchidektDeckDetailDto

/**
 * Platform-neutral cache for community deck (Archidekt) detail responses.
 *
 * The Android implementation wraps [CommunityDeckCacheDao][com.mmg.manahub.core.data.local.dao.CommunityDeckCacheDao]
 * (Room); the web implementation can use IndexedDB / localStorage or skip caching entirely.
 */
interface CommunityDeckCache {

    /**
     * Returns the cached detail DTO and the epoch-millis timestamp it was stored,
     * or `null` if no entry exists for [archidektId].
     */
    suspend fun getById(archidektId: Int): CachedDeckEntry?

    /**
     * Stores [dto] in the cache for the deck identified by [ArchidektDeckDetailDto.id].
     * Implementations handle timestamping internally.
     */
    suspend fun insert(dto: ArchidektDeckDetailDto)
}

/**
 * A cached community deck entry containing the original DTO and the cache timestamp.
 */
data class CachedDeckEntry(
    val dto: ArchidektDeckDetailDto,
    val cachedAt: Long,
)
