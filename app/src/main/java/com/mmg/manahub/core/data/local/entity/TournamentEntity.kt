package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tournaments")
data class TournamentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")               val name:              String,
    @ColumnInfo(name = "format")             val format:            String,   // "COMMANDER"|"STANDARD"|"DRAFT"
    @ColumnInfo(name = "structure")          val structure:         String,   // "ROUND_ROBIN"|"SWISS"|"SINGLE_ELIM"
    @ColumnInfo(name = "status")             val status:            String,   // "SETUP"|"ACTIVE"|"FINISHED"
    @ColumnInfo(name = "matchesPerPairing")  val matchesPerPairing: Int     = 1,
    @ColumnInfo(name = "isRandomPairings")   val isRandomPairings:  Boolean = true,
    @ColumnInfo(name = "createdAt")          val createdAt:         Long    = System.currentTimeMillis(),
    @ColumnInfo(name = "finishedAt")         val finishedAt:        Long?   = null,
)
