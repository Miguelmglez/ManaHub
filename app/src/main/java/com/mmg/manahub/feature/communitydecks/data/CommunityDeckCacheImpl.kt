package com.mmg.manahub.feature.communitydecks.data

import com.mmg.manahub.core.data.cache.CachedDeckEntry
import com.mmg.manahub.core.data.cache.CommunityDeckCache
import com.mmg.manahub.core.data.local.dao.CommunityDeckCacheDao
import com.mmg.manahub.core.data.remote.dto.ArchidektDeckDetailDto
import com.mmg.manahub.feature.communitydecks.data.remote.toCacheEntity
import kotlinx.serialization.json.Json

/**
 * Android (Room-backed) implementation of [CommunityDeckCache].
 *
 * Delegates to [CommunityDeckCacheDao] for persistence and uses the existing
 * [toCacheEntity] mapper to serialise the DTO into the Room entity. On read,
 * the stored JSON blob is deserialised back into an [ArchidektDeckDetailDto].
 */
class CommunityDeckCacheImpl(
    private val cacheDao: CommunityDeckCacheDao,
) : CommunityDeckCache {

    /** Lenient JSON for round-tripping the cached DTO blob. */
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    override suspend fun getById(archidektId: Int): CachedDeckEntry? {
        val entity = cacheDao.getById(archidektId) ?: return null
        val dto = json.decodeFromString(ArchidektDeckDetailDto.serializer(), entity.responseJson)
        return CachedDeckEntry(dto = dto, cachedAt = entity.cachedAt)
    }

    override suspend fun insert(dto: ArchidektDeckDetailDto) {
        cacheDao.insert(dto.toCacheEntity())
    }
}
