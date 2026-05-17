package com.mmg.manahub.feature.draft.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for caching draft set metadata from the Cloudflare Worker sets-index.json.
 *
 * Schema changed in DB version 33 (from v32):
 * - Removed: setType, cardCount, scryfallUri (not present in the new Cloudflare source)
 * - Added: guideVersion, tierListVersion (for local cache invalidation)
 *
 * The primary key is the set code (e.g. "eoe") since the Cloudflare index uses
 * code as the unique identifier, unlike the previous Scryfall-based entity which
 * used Scryfall's UUID.
 */
@Entity(tableName = "draft_sets")
data class DraftSetEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val releasedAt: String,
    val iconSvgUri: String,
    val guideVersion: String,
    val tierListVersion: String,
    val cachedAt: Long = System.currentTimeMillis(),
)
