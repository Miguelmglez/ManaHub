package com.mmg.manahub.feature.competitive.data

import com.mmg.manahub.core.data.cache.CachedCompetitiveEntry
import com.mmg.manahub.core.data.cache.CompetitiveLimitedRatingsCache
import com.mmg.manahub.core.data.local.dao.CompetitiveLimitedRatingsCacheDao
import com.mmg.manahub.core.data.local.entity.CompetitiveLimitedRatingsCacheEntity

/**
 * Android (Room-backed) implementation of [CompetitiveLimitedRatingsCache] — Competitive feature,
 * Phase 5. Mirrors [CompetitiveMetaCacheImpl]/
 * [com.mmg.manahub.feature.decks.data.CommunityAggregateCacheImpl]'s structure: delegates straight
 * through to [cacheDao], no dispatcher switch of its own (the caller,
 * `CompetitiveRepositoryImpl`, already wraps its methods in `withContext(dispatcherProvider.io)`).
 *
 * [CompetitiveLimitedRatingsCacheEntity.format] is a diagnostic-only column (see the entity's
 * KDoc — the true cache key is [key]/`setCode` alone); the commonMain [CompetitiveLimitedRatingsCache]
 * interface never passes a format through, so this stores the fixed default the repository always
 * requests today (`"PremierDraft"`) rather than leaving the column blank.
 */
class CompetitiveLimitedRatingsCacheImpl(
    private val cacheDao: CompetitiveLimitedRatingsCacheDao,
) : CompetitiveLimitedRatingsCache {

    override suspend fun get(key: String): CachedCompetitiveEntry? {
        val entity = cacheDao.getByKey(key) ?: return null
        return CachedCompetitiveEntry(key = entity.setCode, json = entity.responseJson, cachedAt = entity.cachedAt)
    }

    override suspend fun insert(key: String, json: String, cachedAt: Long) {
        cacheDao.upsert(
            CompetitiveLimitedRatingsCacheEntity(
                setCode = key,
                format = DEFAULT_DIAGNOSTIC_FORMAT,
                responseJson = json,
                cachedAt = cachedAt,
            )
        )
    }

    private companion object {
        const val DEFAULT_DIAGNOSTIC_FORMAT = "PremierDraft"
    }
}
