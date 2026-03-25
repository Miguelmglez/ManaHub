package com.mmg.magicfolder.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mana_symbols")
data class ManaSymbolEntity(
    @PrimaryKey val symbol: String,       // "{W}", "{U/R}", etc.
    val svgUri: String,
    val description: String,
    val isHybrid: Boolean,
    val isPhyrexian: Boolean,
    val cachedAt: Long = System.currentTimeMillis(),
)
