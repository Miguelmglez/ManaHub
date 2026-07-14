package com.mmg.manahub.feature.decks.data

import com.mmg.manahub.core.data.cache.CachedAggregateEntry
import com.mmg.manahub.core.data.cache.CommunityAggregateCache
import com.mmg.manahub.core.data.local.dao.CommunityAggregateDao
import com.mmg.manahub.core.data.local.entity.CommunityAggregateEntity

/**
 * Android (Room-backed) implementation of [CommunityAggregateCache] — Deck Doctor
 * Community/Archetype plan, Phase 3.3. Mirrors
 * [com.mmg.manahub.feature.communitydecks.data.CommunityDeckCacheImpl]'s structure: delegates
 * straight through to [cacheDao], no dispatcher switch of its own (the caller,
 * `CommunityAggregateRepositoryImpl`, already wraps its methods in
 * `withContext(dispatcherProvider.io)`).
 */
class CommunityAggregateCacheImpl(
    private val cacheDao: CommunityAggregateDao,
) : CommunityAggregateCache {

    override suspend fun get(key: String): CachedAggregateEntry? {
        val entity = cacheDao.getByKey(key) ?: return null
        return CachedAggregateEntry(key = entity.key, json = entity.json, cachedAt = entity.cachedAt)
    }

    override suspend fun insert(key: String, json: String, cachedAt: Long) {
        cacheDao.upsert(CommunityAggregateEntity(key = key, json = json, cachedAt = cachedAt))
    }
}
