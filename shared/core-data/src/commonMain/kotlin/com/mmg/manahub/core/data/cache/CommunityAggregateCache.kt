package com.mmg.manahub.core.data.cache

/** A cached community aggregate snapshot, stored as its raw JSON response body. */
data class CachedAggregateEntry(val key: String, val json: String, val cachedAt: Long)

/**
 * Platform-neutral cache abstraction for community aggregate snapshots (Phase 3.3): Room on
 * Android, IndexedDB/no-op on web — mirrors the [com.mmg.manahub.core.data.cache.CommunityDeckCache]
 * pattern already established for `CommunityDecksRepositoryImpl`.
 *
 * The cache stores the RAW JSON response body keyed by the SAME cache key convention the
 * Worker itself uses (`agg:commander:<slug>` / `agg:<format>:<canonicalKey>`) rather than a
 * typed row per snapshot shape — this lets one small table serve both the Commander and
 * 60-card snapshot shapes without a schema fork.
 */
interface CommunityAggregateCache {
    suspend fun get(key: String): CachedAggregateEntry?
    suspend fun insert(key: String, json: String, cachedAt: Long)
}
