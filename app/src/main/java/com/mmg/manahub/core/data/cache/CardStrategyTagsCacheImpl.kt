package com.mmg.manahub.core.data.cache

import com.mmg.manahub.core.data.local.dao.CardStrategyTagsCacheDao
import com.mmg.manahub.core.data.local.entity.CardStrategyTagsCacheEntity

/**
 * Android (Room-backed) implementation of [CardStrategyTagsCache] — Deck Engine Unification plan,
 * D8, §5 Phase 5c. Mirrors `ComboCacheImpl`'s structure exactly: delegates straight through to
 * [cacheDao], no dispatcher switch of its own (callers already wrap their methods in
 * `withContext(dispatcherProvider.io)`).
 */
class CardStrategyTagsCacheImpl(
    private val cacheDao: CardStrategyTagsCacheDao,
) : CardStrategyTagsCache {

    override suspend fun get(oracleId: String): CachedCardStrategyTagsEntry? {
        val entity = cacheDao.getByOracleId(oracleId) ?: return null
        return CachedCardStrategyTagsEntry(
            oracleId        = entity.oracleId,
            payloadJson     = entity.payloadJson,
            pipelineVersion = entity.pipelineVersion,
            generatedAt     = entity.generatedAt,
            fetchedAt       = entity.fetchedAt,
        )
    }

    override suspend fun insert(entry: CachedCardStrategyTagsEntry) {
        cacheDao.upsert(
            CardStrategyTagsCacheEntity(
                oracleId        = entry.oracleId,
                payloadJson     = entry.payloadJson,
                pipelineVersion = entry.pipelineVersion,
                generatedAt     = entry.generatedAt,
                fetchedAt       = entry.fetchedAt,
            )
        )
    }
}
