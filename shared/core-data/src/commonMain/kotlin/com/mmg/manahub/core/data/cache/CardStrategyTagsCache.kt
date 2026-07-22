package com.mmg.manahub.core.data.cache

/**
 * A cached `card_strategy_tags` row, stored as the raw payload JSON verbatim (same "store the raw
 * response body" convention as [CommunityAggregateCache]/`ComboCache`) plus the two small metadata
 * columns needed to decide freshness / surface pipeline provenance without re-parsing the payload.
 */
data class CachedCardStrategyTagsEntry(
    val oracleId: String,
    val payloadJson: String,
    val pipelineVersion: String,
    val generatedAt: String,
    val fetchedAt: Long,
)

/**
 * Platform-neutral cache abstraction for precomputed card strategy tags (Deck Engine Unification
 * plan, D8, §5 Phase 5c): Room on Android, no-op/IndexedDB on web — mirrors
 * [CommunityAggregateCache]'s shape exactly.
 */
interface CardStrategyTagsCache {
    suspend fun get(oracleId: String): CachedCardStrategyTagsEntry?
    suspend fun insert(entry: CachedCardStrategyTagsEntry)
}
