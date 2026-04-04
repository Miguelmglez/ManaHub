package com.mmg.magicfolder.feature.news.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "news_articles")
data class NewsArticleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")           val id: String,
    @ColumnInfo(name = "title")        val title: String,
    @ColumnInfo(name = "description")  val description: String,
    @ColumnInfo(name = "image_url")    val imageUrl: String?,
    @ColumnInfo(name = "published_at") val publishedAt: Long,
    @ColumnInfo(name = "source_name")  val sourceName: String,
    @ColumnInfo(name = "source_id")    val sourceId: String,
    @ColumnInfo(name = "url")          val url: String,
    @ColumnInfo(name = "author")       val author: String?,
    @ColumnInfo(name = "fetched_at")   val fetchedAt: Long = System.currentTimeMillis(),
)
