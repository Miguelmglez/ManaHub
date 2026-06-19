package com.mmg.manahub.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-seat card-impact rating captured by the post-game survey (DB v37, see ADR-001).
 *
 * Each row records that, for a given finished game session and a given seat, a card
 * was flagged as either the player's MVP (most valuable) or DEAD (drew it but it did
 * nothing). This is the per-seat, per-card matchup signal that aggregate stats build on.
 *
 * All three foreign keys cascade on delete:
 * - deleting a [GameSessionEntity] removes its impacts,
 * - deleting a [PlayerSessionEntity] (its seat) removes that seat's impacts,
 * - deleting a [CardEntity] removes impacts referencing it.
 */
@Entity(
    tableName = "survey_card_impacts",
    foreignKeys = [
        ForeignKey(
            entity        = GameSessionEntity::class,
            parentColumns = ["id"],
            childColumns  = ["session_id"],
            onDelete      = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity        = PlayerSessionEntity::class,
            parentColumns = ["id"],
            childColumns  = ["player_session_id"],
            onDelete      = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity        = CardEntity::class,
            parentColumns = ["scryfall_id"],
            childColumns  = ["card_id"],
            onDelete      = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("session_id"),
        Index("player_session_id"),
        Index("card_id"),
    ],
)
data class SurveyCardImpactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @androidx.room.ColumnInfo(name = "session_id")
    val sessionId: Long,
    @androidx.room.ColumnInfo(name = "player_session_id")
    val playerSessionId: Long,
    @androidx.room.ColumnInfo(name = "card_id")
    val cardId: String,
    /** "MVP" or "DEAD". */
    @androidx.room.ColumnInfo(name = "impact")
    val impact: String,
)
