package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.mmg.manahub.core.data.local.entity.SurveyAnswerEntity
import kotlinx.coroutines.flow.Flow

data class CardScoreRow(
    val cardReference: String?,
    val appearances:   Int,
    val avgScore:      Double,
)

data class AnswerCount(val answer: String, val count: Int)

@Dao
abstract class SurveyAnswerDao {

    // ── Write ──────────────────────────────────────────────────────────────────

    @Insert
    abstract suspend fun insertAnswers(answers: List<SurveyAnswerEntity>)

    @Query("DELETE FROM survey_answers WHERE sessionId = :sessionId")
    abstract suspend fun deleteForSession(sessionId: Long)

    /**
     * Atomically replaces all answers for a session. Used for edit/REVIEW mode and for
     * the auto-save-by-panel flow so that re-answering doesn't accumulate stale rows.
     *
     * Intentionally NOT using OnConflictStrategy.REPLACE on the entity: the project
     * convention is to avoid REPLACE since it does DELETE + INSERT which would cascade
     * to dependent tables. A scoped DELETE + INSERT inside a @Transaction is explicit
     * and safe.
     */
    @Transaction
    open suspend fun replaceAnswersForSession(sessionId: Long, answers: List<SurveyAnswerEntity>) {
        deleteForSession(sessionId)
        if (answers.isNotEmpty()) insertAnswers(answers)
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM survey_answers WHERE sessionId = :id")
    abstract suspend fun getAnswersForSession(id: Long): List<SurveyAnswerEntity>

    @Query("SELECT * FROM survey_answers WHERE sessionId = :id")
    abstract fun observeAnswersForSession(id: Long): Flow<List<SurveyAnswerEntity>>

    // ── Card-impact insights ───────────────────────────────────────────────────

    // KEY_CARD=2, AVERAGE=1, WEAK=0 — used globally by DeckBuilder when no deck filter applies.
    @Query("""
        SELECT COALESCE(AVG(
            CASE answer
                WHEN 'KEY_CARD' THEN 2.0
                WHEN 'AVERAGE'  THEN 1.0
                WHEN 'WEAK'     THEN 0.0
                ELSE 1.0
            END
        ), 1.0)
        FROM survey_answers
        WHERE cardReference = :scryfallId
        AND questionType = 'CARD_IMPACT'
    """)
    abstract fun observeCardImpactScore(scryfallId: String): Flow<Double>

    @Query("""
        SELECT sa.cardReference,
               COUNT(*) AS appearances,
               AVG(CASE answer
                   WHEN 'KEY_CARD' THEN 2.0
                   WHEN 'AVERAGE'  THEN 1.0
                   WHEN 'WEAK'     THEN 0.0
                   ELSE 1.0 END) AS avgScore
        FROM survey_answers sa
        WHERE sa.questionType = 'CARD_IMPACT'
        AND sa.cardReference IS NOT NULL
        GROUP BY sa.cardReference
        ORDER BY avgScore ASC
        LIMIT :limit
    """)
    abstract fun observeWeakestCards(limit: Int): Flow<List<CardScoreRow>>

    // Per-deck card-impact aggregates (used by DeckStatsCard).
    @Query("""
        SELECT sa.cardReference,
               COUNT(*) AS appearances,
               AVG(CASE answer
                   WHEN 'KEY_CARD' THEN 2.0
                   WHEN 'AVERAGE'  THEN 1.0
                   WHEN 'WEAK'     THEN 0.0
                   ELSE 1.0 END) AS avgScore
        FROM survey_answers sa
        WHERE sa.questionType = 'CARD_IMPACT'
        AND sa.cardReference IS NOT NULL
        AND sa.deckId = :deckId
        GROUP BY sa.cardReference
        ORDER BY avgScore DESC
        LIMIT :limit
    """)
    abstract fun observeTopCardsForDeck(deckId: String, limit: Int): Flow<List<CardScoreRow>>

    @Query("""
        SELECT sa.cardReference,
               COUNT(*) AS appearances,
               AVG(CASE answer
                   WHEN 'KEY_CARD' THEN 2.0
                   WHEN 'AVERAGE'  THEN 1.0
                   WHEN 'WEAK'     THEN 0.0
                   ELSE 1.0 END) AS avgScore
        FROM survey_answers sa
        WHERE sa.questionType = 'CARD_IMPACT'
        AND sa.cardReference IS NOT NULL
        AND sa.deckId = :deckId
        GROUP BY sa.cardReference
        ORDER BY avgScore ASC
        LIMIT :limit
    """)
    abstract fun observeWeakestCardsForDeck(deckId: String, limit: Int): Flow<List<CardScoreRow>>

    // ── Profile insights ───────────────────────────────────────────────────────

    @Query("""
        SELECT COUNT(*) FROM survey_answers
        WHERE questionType = 'MANA'
        AND answer NOT IN ('SMOOTH', 'NONE')
    """)
    abstract fun observeManaIssueCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT sessionId) FROM survey_answers")
    abstract fun observeSurveyCount(): Flow<Int>

    @Query("""
        SELECT AVG(CAST(answer AS REAL))
        FROM survey_answers
        WHERE questionType = 'HAND'
    """)
    abstract fun observeAvgHandRating(): Flow<Double?>

    @Query("""
        SELECT answer, COUNT(*) AS count
        FROM survey_answers
        WHERE questionType = 'RESULT_FEEL'
        AND answer IN ('DOMINANT', 'CLOSE', 'LUCKY', 'SKILLFUL')
        GROUP BY answer
        ORDER BY count DESC
        LIMIT 1
    """)
    abstract fun observeFavoriteWinStyle(): Flow<AnswerCount?>
}
