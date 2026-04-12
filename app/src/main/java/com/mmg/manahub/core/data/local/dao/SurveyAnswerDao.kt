package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mmg.manahub.core.data.local.entity.SurveyAnswerEntity
import kotlinx.coroutines.flow.Flow

data class CardScoreRow(
    val cardReference: String?,
    val appearances:   Int,
    val avgScore:      Double,
)

data class AnswerCount(val answer: String, val count: Int)

@Dao
interface SurveyAnswerDao {

    @Insert
    suspend fun insertAnswers(answers: List<SurveyAnswerEntity>)

    @Query("SELECT * FROM survey_answers WHERE sessionId = :id")
    suspend fun getAnswersForSession(id: Long): List<SurveyAnswerEntity>

    // Histórico de impacto de una carta: KEY_CARD=2, AVERAGE=1, WEAK=0
    // Usado por DeckBuilder para scoring futuro
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
    fun observeCardImpactScore(scryfallId: String): Flow<Double>

    // Para futura pantalla de insights del mazo
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
    fun observeWeakestCards(limit: Int = 5): Flow<List<CardScoreRow>>

    // ── Profile insights ───────────────────────────────────────────────────────

    @Query("""
        SELECT COUNT(*) FROM survey_answers
        WHERE questionType = 'MANA'
        AND answer NOT IN ('SMOOTH', 'NONE')
    """)
    fun observeManaIssueCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT sessionId) FROM survey_answers")
    fun observeSurveyCount(): Flow<Int>

    @Query("""
        SELECT AVG(CAST(answer AS REAL))
        FROM survey_answers
        WHERE questionType = 'HAND'
    """)
    fun observeAvgHandRating(): Flow<Double?>

    @Query("""
        SELECT answer, COUNT(*) AS count
        FROM survey_answers
        WHERE questionType = 'RESULT_FEEL'
        AND answer IN ('DOMINANT', 'CLOSE', 'LUCKY', 'SKILLFUL')
        GROUP BY answer
        ORDER BY count DESC
        LIMIT 1
    """)
    fun observeFavoriteWinStyle(): Flow<AnswerCount?>
}
