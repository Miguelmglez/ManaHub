package com.mmg.manahub.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_sessions")
data class GameSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id:          Long   = 0,
    val playedAt:    Long   = System.currentTimeMillis(),
    val durationMs:  Long,
    val mode:        String,           // "STANDARD" | "COMMANDER"
    val totalTurns:  Int,
    val playerCount: Int,
    val winnerId:    Int,
    val winnerName:  String,
    val notes:       String = "",
)
