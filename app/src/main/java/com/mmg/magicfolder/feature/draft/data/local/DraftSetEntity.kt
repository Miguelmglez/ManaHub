package com.mmg.magicfolder.feature.draft.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "draft_sets")
data class DraftSetEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val setType: String,
    val releasedAt: String,
    val iconSvgUri: String,
    val cardCount: Int,
    val scryfallUri: String,
    val cachedAt: Long = System.currentTimeMillis(),
)
