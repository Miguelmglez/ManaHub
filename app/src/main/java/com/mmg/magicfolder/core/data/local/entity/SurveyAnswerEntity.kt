package com.mmg.magicfolder.core.data.local.entity

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
    indices = [Index("sessionId")],
)
data class SurveyAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id:          Long   = 0,
    val sessionId:   Long,
    val questionKey: String,   // e.g. "overall_rating", "game_pace"
    val answerJson:  String,   // serialized answer (plain value or JSON array for multi-choice)
    val answeredAt:  Long      = System.currentTimeMillis(),
)
