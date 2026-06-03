package com.mmg.manahub.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Optional survey answers attached to a saved playtest session.
 *
 * Mirrors [SurveyAnswerEntity] but references [PlaytestSessionEntity] instead
 * of [GameSessionEntity]. Rows exist only when the user fills the optional
 * post-test survey — leaving the survey empty results in zero rows here for
 * that playtestSessionId.
 *
 * The survey is always additive: it does not alter the session or card-stat rows.
 * Write pattern: [PlaytestDao.replacePlaytestSurveyAnswers] (scoped DELETE + INSERT
 * inside @Transaction) — never OnConflictStrategy.REPLACE.
 *
 * deckId denormalization rationale:
 *   Survey aggregate queries (e.g. "how often did I feel my opening hand was good
 *   with this deck?") filter by deckId without needing a JOIN to playtest_sessions.
 *   This matches the pattern already established in [SurveyAnswerEntity].
 */
@Entity(
    tableName = "playtest_survey_answers",
    foreignKeys = [
        ForeignKey(
            entity = PlaytestSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["playtest_session_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["playtest_session_id"], name = "index_playtest_survey_answers_session_id"),
        Index(value = ["card_reference"], name = "index_playtest_survey_answers_card_reference"),
        Index(value = ["deck_id"], name = "index_playtest_survey_answers_deck_id"),
    ],
)
data class PlaytestSurveyAnswerEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** FK → playtest_sessions.id. Cascades on delete. */
    @ColumnInfo(name = "playtest_session_id")
    val playtestSessionId: Long,

    /** Stable question identifier (e.g. "HAND_FEEL", "MANA_CURVE"). */
    @ColumnInfo(name = "question_id")
    val questionId: String,

    /** Discriminator for aggregate queries (e.g. "HAND", "CARD_IMPACT", "MANA"). */
    @ColumnInfo(name = "question_type")
    val questionType: String,

    /** Serialized answer value (text, numeric rating as string, enum name, etc.). */
    @ColumnInfo(name = "answer")
    val answer: String,

    /**
     * scryfallId of the card this answer refers to, for per-card questions
     * (e.g. "Was this card good in your opening hand?"). Null for global questions.
     */
    @ColumnInfo(name = "card_reference")
    val cardReference: String? = null,

    /**
     * Denormalized deck UUID. Copied from PlaytestSessionEntity.deckId at write
     * time so that per-deck survey aggregate queries run as simple WHERE filters
     * without a JOIN to playtest_sessions.
     */
    @ColumnInfo(name = "deck_id")
    val deckId: String? = null,

    @ColumnInfo(name = "answered_at")
    val answeredAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
