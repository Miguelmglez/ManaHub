package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per seat (player) in a finished game session.
 *
 * Per-seat identity and matchup data added in DB v37 (see ADR-001):
 * - [isLocal] marks the seat owned by the app user. This is the authoritative
 *   source for win/loss determination in the post-game survey — never infer the
 *   app user from [isWinner] (every finished game has a winner, so that inference
 *   always reports a win).
 * - [deckId] is the UUID of the deck the seat played (replaces the legacy
 *   `Long?` id, which never mapped to the app's String UUID deck ids).
 * - [archetype] and [linkedProfileTag] are forward-looking hooks for cross-player,
 *   cross-deck matchup statistics. All three are nullable and degrade gracefully
 *   when unset.
 */
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

    /** UUID of the deck this seat played, or null when unknown. Was a legacy `Long?` before v37. */
    @ColumnInfo(name = "deck_id")
    val deckId:                  String? = null,

    val deckName:                String? = null,
    val opponentColors:          String  = "",   // JSON: ["W","U"]
    val isWinner:                Boolean = false,

    /** True for the seat owned by the app user. Authoritative source for survey win/loss. */
    @ColumnInfo(name = "is_local", defaultValue = "0")
    val isLocal:                 Boolean = false,

    /** Deck archetype for this seat: AGGRO|MIDRANGE|CONTROL|COMBO|RAMP|OTHER. Null when unclassified. */
    @ColumnInfo(name = "archetype")
    val archetype:               String? = null,

    /** Cross-player stats hook: the linked account's ProfileTag for this seat (server v2). Null in local v1. */
    @ColumnInfo(name = "linked_profile_tag")
    val linkedProfileTag:        String? = null,
)
