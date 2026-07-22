package com.mmg.manahub.core.data.cache

/** A cached `find-my-combos` response, stored as its raw JSON response body. */
data class CachedComboEntry(val key: String, val json: String, val cachedAt: Long)

/**
 * Platform-neutral cache abstraction for Commander Spellbook `find-my-combos` responses (Deck
 * Engine Unification plan D7, Phase 4.3) -- Room on Android, IndexedDB/no-op on web, mirrors
 * [CommunityAggregateCache] exactly (same "raw JSON, one small table" rationale).
 */
interface ComboCache {
    suspend fun get(key: String): CachedComboEntry?
    suspend fun insert(key: String, json: String, cachedAt: Long)
}
