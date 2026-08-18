package com.mmg.manahub.core.data.cache

/** A cached competitive snapshot, stored as its raw JSON response body. */
data class CachedCompetitiveEntry(val key: String, val json: String, val cachedAt: Long)

/**
 * Platform-neutral cache abstraction for weekly meta snapshots (Competitive feature, Phase 3):
 * Room on Android (adapting `CompetitiveMetaCacheDao`, `app/.../core/data/local/dao/`), IndexedDB/
 * no-op on web — mirrors the [CommunityAggregateCache] pattern already established for the
 * sibling `manahub-community` Worker. `commonMain` cannot depend on the Room DAO directly (Room
 * has no wasmJs target, and the DAO lives in the `:app` module which `:shared:core-data` cannot
 * depend on without inverting the module graph) — this interface is the seam an Android-side
 * adapter implements, wired via Koin in a later phase.
 *
 * Keyed by format (`"standard"`, `"modern"`, ...), matching [CompetitiveMetaCacheEntity]'s
 * primary key.
 */
interface CompetitiveMetaCache {
    suspend fun get(key: String): CachedCompetitiveEntry?
    suspend fun insert(key: String, json: String, cachedAt: Long)
}

/**
 * Platform-neutral cache abstraction for 17lands Limited ratings snapshots (Competitive feature,
 * Phase 3) — same rationale and wiring plan as [CompetitiveMetaCache].
 *
 * Keyed by set code alone, matching [CompetitiveLimitedRatingsCacheEntity]'s primary key
 * decision: the true upstream identity is (set code, draft format), but this cache treats set
 * code as the sole key for v1 simplicity since the app only ever queries `PremierDraft` ratings
 * today. A request for a second format on an already-cached set would silently serve/overwrite
 * that set's single cache slot — acceptable for v1, called out here so a future caller adding a
 * second format widens the key (or the interface) rather than hitting this by surprise.
 */
interface CompetitiveLimitedRatingsCache {
    suspend fun get(key: String): CachedCompetitiveEntry?
    suspend fun insert(key: String, json: String, cachedAt: Long)
}
