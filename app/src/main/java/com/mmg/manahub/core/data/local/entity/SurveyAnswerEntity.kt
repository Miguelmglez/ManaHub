package com.mmg.manahub.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "survey_answers",
    foreignKeys = [
        ForeignKey(
            entity        = GameSessionEntity::class,
            parentColumns = ["id"],
            childColumns  = ["sessionId"],
            onDelete      = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["sessionId"], name = "index_survey_answers_sessionId"),
        Index(value = ["cardReference"], name = "index_survey_answers_cardReference"),
        Index(value = ["deckId"], name = "index_survey_answers_deckId"),
    ],
)
data class SurveyAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId:     Long,
    val questionId:    String,
    val questionType:  String,
    val answer:        String,
    val cardReference: String? = null,
    val deckId:        String? = null,                       // UUID of the user's deck for this game (denormalized for fast per-deck queries)
    val answeredAt:    Long    = System.currentTimeMillis(),
    val updatedAt:     Long    = System.currentTimeMillis(),
)
