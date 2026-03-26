package com.mmg.magicfolder.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "player_sessions",
    foreignKeys = [
        ForeignKey(
            entity       = GameSessionEntity::class,
            parentColumns = ["id"],
            childColumns  = ["sessionId"],
            onDelete      = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sessionId")],
)
data class PlayerSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id:                      Long    = 0,
    val sessionId:               Long,
    val playerId:                Int,
    val playerName:              String,
    val finalLife:               Int,
    val finalPoison:             Int,
    val eliminationReason:       String?,   // "LIFE"|"POISON"|"COMMANDER_DAMAGE"|null
    val commanderDamageDealt:    Int     = 0,
    val commanderDamageReceived: Int     = 0,
    val deckId:                  Long?   = null,
    val deckName:                String? = null,
    val opponentColors:          String  = "",   // JSON: ["W","U"]
    val isWinner:                Boolean = false,
)
