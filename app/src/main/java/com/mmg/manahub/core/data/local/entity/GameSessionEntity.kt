package com.mmg.manahub.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Survey lifecycle states attached to a finished game.
 *
 * - PENDING:   the post-game survey has not been opened yet.
 * - PARTIAL:   the user opened the survey and answered at least one question, but tapped "Later".
 * - COMPLETED: the user finished the survey via "Save & finish".
 * - SKIPPED:   the user dismissed the survey entirely (close button or skipAll).
 */
enum class SurveyStatus { PENDING, PARTIAL, COMPLETED, SKIPPED }

@Entity(tableName = "game_sessions")
data class GameSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id:                Long   = 0,
    val playedAt:          Long   = System.currentTimeMillis(),
    val durationMs:        Long,
    val mode:              String,           // "STANDARD" | "COMMANDER"
    val totalTurns:        Int,
    val playerCount:       Int,
    val winnerId:          Int,
    val winnerName:        String,
    val notes:             String = "",
    val surveyStatus:      String = SurveyStatus.PENDING.name,
    val surveyCompletedAt: Long?  = null,
    val deckId:            String? = null,                  // UUID of the deck the app user played (set when survey associates one)
)
