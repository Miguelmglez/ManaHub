package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "content_sources")
data class ContentSourceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")         val id: String,
    @ColumnInfo(name = "name")       val name: String,
    @ColumnInfo(name = "feed_url")   val feedUrl: String,
    @ColumnInfo(name = "type")       val type: String,      // "ARTICLE" or "VIDEO"
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean = true,
    @ColumnInfo(name = "is_default") val isDefault: Boolean = true,
    @ColumnInfo(name = "icon_url")   val iconUrl: String? = null,
    @ColumnInfo(name = "language")   val language: String = "en", // "en", "es", "de"
    /**
     * Wall-clock time (millis) this source was last SUCCESSFULLY fetched (200 or 304),
     * per-source refresh watermark (v45). `0` means "never fetched" (always stale).
     */
    @ColumnInfo(name = "last_fetched_at", defaultValue = "0")
    val lastFetchedAt: Long = 0L,
    /** HTTP `ETag` response header captured on the last 200, used for conditional GET (v45). */
    @ColumnInfo(name = "etag")
    val etag: String? = null,
    /** HTTP `Last-Modified` response header captured on the last 200 (v45). */
    @ColumnInfo(name = "last_modified")
    val lastModified: String? = null,
)
