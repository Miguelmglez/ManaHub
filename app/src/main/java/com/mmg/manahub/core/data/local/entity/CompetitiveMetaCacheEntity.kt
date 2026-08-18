package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of a fetched weekly MTG metagame rankings snapshot for a given [format], served by
 * the `manahub-competitive` Cloudflare Worker (Competitive feature, Phase 2).
 *
 * Mirrors [CommunityAggregateEntity]'s "pure cache, store the raw response verbatim" pattern,
 * keyed by [format] rather than a typed row per response shape, so a schema change to the
 * Worker's payload never requires a Room migration. Holds only transient, re-fetchable data (NOT
 * user-owned data), so it is safe to overwrite on conflict and to evict by age.
 */
@Entity(tableName = "competitive_meta_cache")
data class CompetitiveMetaCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "format") val format: String,
    @ColumnInfo(name = "response_json") val responseJson: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis(),
)
