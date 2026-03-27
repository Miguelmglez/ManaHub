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
    indices = [
        Index("sessionId"),
        Index("cardReference"),
    ],
)
data class SurveyAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId:     Long,
    val questionId:    String,
    val questionType:  String,
    val answer:        String,
    val cardReference: String? = null,
    val answeredAt:    Long    = System.currentTimeMillis(),
)
