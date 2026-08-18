package com.mmg.manahub.feature.competitive.data

import com.mmg.manahub.core.data.cache.CachedCompetitiveEntry
import com.mmg.manahub.core.data.cache.CompetitiveMetaCache
import com.mmg.manahub.core.data.local.dao.CompetitiveMetaCacheDao
import com.mmg.manahub.core.data.local.entity.CompetitiveMetaCacheEntity

/**
 * Android (Room-backed) implementation of [CompetitiveMetaCache] — Competitive feature, Phase 5.
 * Mirrors [com.mmg.manahub.feature.decks.data.CommunityAggregateCacheImpl]'s structure: delegates
 * straight through to [cacheDao], no dispatcher switch of its own (the caller,
 * `CompetitiveRepositoryImpl`, already wraps its methods in `withContext(dispatcherProvider.io)`).
 */
class CompetitiveMetaCacheImpl(
    private val cacheDao: CompetitiveMetaCacheDao,
) : CompetitiveMetaCache {

    override suspend fun get(key: String): CachedCompetitiveEntry? {
        val entity = cacheDao.getByKey(key) ?: return null
        return CachedCompetitiveEntry(key = entity.format, json = entity.responseJson, cachedAt = entity.cachedAt)
    }

    override suspend fun insert(key: String, json: String, cachedAt: Long) {
        cacheDao.upsert(CompetitiveMetaCacheEntity(format = key, responseJson = json, cachedAt = cachedAt))
    }
}
