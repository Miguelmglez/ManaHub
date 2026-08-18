package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of a fetched 17lands Limited card-ratings snapshot for a given [setCode], served by
 * the `manahub-competitive` Cloudflare Worker (Competitive feature, Phase 2).
 *
 * Mirrors [CompetitiveMetaCacheEntity]'s "pure cache, store the raw response verbatim" pattern.
 * The true upstream identity is (`set_code`, `format`) — 17lands ratings differ per draft format
 * (PremierDraft / TradDraft / etc.) — but this cache treats [setCode] alone as the primary key
 * for v1 simplicity, since the app only ever queries PremierDraft ratings today. [format] is kept
 * as a plain column for traceability/debugging; widening the key to a composite (or to an opaque
 * `set_code:format` string, matching [CommunityAggregateEntity]'s convention) is a purely
 * additive follow-up if a second format ever needs caching. Holds only transient, re-fetchable
 * data (NOT user-owned data), so it is safe to overwrite on conflict and to evict by age.
 */
@Entity(tableName = "competitive_limited_ratings_cache")
data class CompetitiveLimitedRatingsCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "set_code") val setCode: String,
    @ColumnInfo(name = "format") val format: String,
    @ColumnInfo(name = "response_json") val responseJson: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis(),
)
