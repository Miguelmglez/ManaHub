package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of a fetched community deck (Archidekt) detail response.
 *
 * This is a pure cache table: the full upstream JSON is stored verbatim in
 * [responseJson] so the detail screen can re-render without a network round-trip,
 * while the denormalized columns (name/owner/format/...) support cheap list-level
 * lookups and display without parsing the blob.
 *
 * Because it holds only transient, re-fetchable data (NOT user-owned data), it is
 * safe to overwrite on conflict and to evict by age.
 */
@Entity(tableName = "community_deck_cache")
data class CommunityDeckCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "archidekt_id") val archidektId: Int,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "owner_username") val ownerUsername: String,
    @ColumnInfo(name = "format") val format: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "view_count") val viewCount: Int,
    @ColumnInfo(name = "card_count") val cardCount: Int,
    @ColumnInfo(name = "response_json") val responseJson: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis(),
)
