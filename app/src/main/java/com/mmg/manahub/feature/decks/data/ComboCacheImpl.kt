package com.mmg.manahub.feature.decks.data

import com.mmg.manahub.core.data.cache.CachedComboEntry
import com.mmg.manahub.core.data.cache.ComboCache
import com.mmg.manahub.core.data.local.dao.ComboCacheDao
import com.mmg.manahub.core.data.local.entity.ComboCacheEntity

/**
 * Android (Room-backed) implementation of [ComboCache] — Deck Engine Unification plan D7,
 * Phase 4.3. Mirrors [CommunityAggregateCacheImpl]'s structure exactly: delegates straight
 * through to [cacheDao], no dispatcher switch of its own (the caller,
 * `CommanderSpellbookRepositoryImpl`, already wraps its methods in
 * `withContext(dispatcherProvider.io)`).
 */
class ComboCacheImpl(
    private val cacheDao: ComboCacheDao,
) : ComboCache {

    override suspend fun get(key: String): CachedComboEntry? {
        val entity = cacheDao.getByKey(key) ?: return null
        return CachedComboEntry(key = entity.key, json = entity.json, cachedAt = entity.cachedAt)
    }

    override suspend fun insert(key: String, json: String, cachedAt: Long) {
        cacheDao.upsert(ComboCacheEntity(key = key, json = json, cachedAt = cachedAt))
    }
}
