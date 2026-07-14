package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of a fetched community aggregate snapshot (Commander via EDHREC or 60-card via
 * Archidekt) served by the `manahub-community` Cloudflare Worker (Deck Doctor Community/Archetype
 * plan, Phase 3.3; see `docs/adr/ADR-004-community-api-contracts.md`).
 *
 * Mirrors [com.mmg.manahub.core.data.local.entity.CommunityDeckCacheEntity]'s "pure cache, store
 * the raw response verbatim" pattern, but keyed by [key] — the SAME cache-key convention the
 * Worker itself uses (`agg:commander:<slug>` / `agg:<format>:<canonicalKey>`) rather than a typed
 * row per snapshot shape, so one small table serves both the Commander and 60-card aggregate
 * shapes without a schema fork. Because it holds only transient, re-fetchable data (NOT
 * user-owned data), it is safe to overwrite on conflict and to evict by age.
 */
@Entity(tableName = "community_aggregate_cache")
data class CommunityAggregateEntity(
    @PrimaryKey
    @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "json") val json: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long,
)
