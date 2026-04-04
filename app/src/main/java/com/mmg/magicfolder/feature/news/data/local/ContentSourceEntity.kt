package com.mmg.magicfolder.feature.news.data.local

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
)
