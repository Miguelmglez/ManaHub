package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Snapshot of a saved article/video; its own table so it survives the 7-day feed eviction and source removal. */
@Entity(tableName = "news_saved_items")
data class NewsSavedItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")           val id: String,
    @ColumnInfo(name = "kind")         val kind: String, // "ARTICLE" or "VIDEO"
    @ColumnInfo(name = "title")        val title: String,
    @ColumnInfo(name = "description")  val description: String,
    @ColumnInfo(name = "image_url")    val imageUrl: String?,
    @ColumnInfo(name = "published_at") val publishedAt: Long,
    @ColumnInfo(name = "source_id")    val sourceId: String,
    @ColumnInfo(name = "source_name")  val sourceName: String,
    @ColumnInfo(name = "url")          val url: String,
    @ColumnInfo(name = "author")       val author: String?,
    @ColumnInfo(name = "video_id")     val videoId: String?,
    @ColumnInfo(name = "channel_name") val channelName: String?,
    @ColumnInfo(name = "duration")     val duration: String?,
    @ColumnInfo(name = "saved_at")     val savedAt: Long,
)
