package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.mmg.manahub.core.data.local.entity.PlaytestCardStatEntity
import com.mmg.manahub.core.data.local.entity.PlaytestSessionEntity
import com.mmg.manahub.core.data.local.entity.PlaytestSurveyAnswerEntity
import kotlinx.coroutines.flow.Flow

// ── Result data classes ────────────────────────────────────────────────────────

/**
 * Aggregate counts for a single card across all saved tests for a deck.
 * Used to compute "this card appeared in your opening hand X / N times".
 */
data class PlaytestCardAppearanceRow(
    val scryfallId: String,
    /** Number of saved tests in which this card appeared in the final opening hand. */
    val testsWithAppearance: Int,
    /** Sum of all copies seen in opening hands across all tests. */
    val totalCopiesInHand: Int,
    /** Sum of all copies bottomed (sent to library bottom) across all tests. */
    val totalCopiesBottomed: Int,
)

/**
 * Appearance rate for a single card relative to total saved tests for the deck.
 * appearedInCount / totalTests = appearance rate.
 */
data class PlaytestCardRateRow(
    val scryfallId: String,
    val appearedInCount: Int,
    val totalTests: Int,
)

// ── DAO ───────────────────────────────────────────────────────────────────────

@Dao
abstract class PlaytestDao {

    // ── Write ──────────────────────────────────────────────────────────────────

    @Insert
    protected abstract suspend fun insertSession(session: PlaytestSessionEntity): Long

    @Insert
    protected abstract suspend fun insertCardStats(stats: List<PlaytestCardStatEntity>)

    /**
     * Atomically saves a playtest session together with all its card-stat rows.
     *
     * This is the ONLY sanctioned entry point for persisting a test. The UI must
     * call this on "Guardar test" — never call insertSession + insertCardStats
     * separately, as a crash between the two calls would produce an orphan session
     * with no card data, corrupting per-deck aggregate queries.
     *
     * @param session  The session to save (id = 0 → auto-generated).
     * @param cardStats Card-stat rows for this session (playtestSessionId will be
     *                  overwritten with the newly generated session id before insert).
     * @return The auto-generated session id assigned by Room.
     */
    @Transaction
    open suspend fun saveTestAtomically(
        session: PlaytestSessionEntity,
        cardStats: List<PlaytestCardStatEntity>,
    ): Long {
        val newSessionId = insertSession(session)
        if (cardStats.isNotEmpty()) {
            val stamped = cardStats.map { it.copy(playtestSessionId = newSessionId) }
            insertCardStats(stamped)
        }
        return newSessionId
    }

    // ── Survey write ───────────────────────────────────────────────────────────

    @Insert
    protected abstract suspend fun insertSurveyAnswers(answers: List<PlaytestSurveyAnswerEntity>)

    @Query("DELETE FROM playtest_survey_answers WHERE playtest_session_id = :playtestSessionId")
    protected abstract suspend fun deleteSurveyAnswersForSession(playtestSessionId: Long)

    /**
     * Atomically replaces all survey answers for a playtest session.
     *
     * Follows the same scoped DELETE + INSERT pattern as [SurveyAnswerDao.replaceAnswersForSession].
     * Intentionally avoids OnConflictStrategy.REPLACE — see CLAUDE.md for the project-wide
     * rationale (REPLACE does DELETE + INSERT which cascades to dependent tables).
     */
    @Transaction
    open suspend fun replacePlaytestSurveyAnswers(
        playtestSessionId: Long,
        answers: List<PlaytestSurveyAnswerEntity>,
    ) {
        deleteSurveyAnswersForSession(playtestSessionId)
        if (answers.isNotEmpty()) insertSurveyAnswers(answers)
    }

    // ── Session reads ──────────────────────────────────────────────────────────

    @Query("SELECT * FROM playtest_sessions WHERE id = :id")
    abstract suspend fun getSessionById(id: Long): PlaytestSessionEntity?

    @Query(
        """
        SELECT * FROM playtest_sessions
        WHERE deck_id = :deckId
        ORDER BY saved_at DESC
        """
    )
    abstract fun observeSessionsForDeck(deckId: String): Flow<List<PlaytestSessionEntity>>

    // ── Aggregate: total saved tests for a deck ────────────────────────────────

    @Query("SELECT COUNT(*) FROM playtest_sessions WHERE deck_id = :deckId")
    abstract fun observeTestCountForDeck(deckId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM playtest_sessions WHERE deck_id = :deckId")
    abstract suspend fun getTestCountForDeck(deckId: String): Int

    // ── Aggregate: per-card opening-hand stats (all cards in a deck) ───────────

    /**
     * For every distinct scryfallId that has appeared in at least one saved test for
     * [deckId], returns the count of tests with an appearance, the sum of all opening-hand
     * copies, and the sum of all bottomed copies.
     *
     * Consumers compute:
     *   - appearance rate = testsWithAppearance / total tests for deck
     *   - avg copies per test = totalCopiesInHand / testsWithAppearance
     *   - bottom rate = totalCopiesBottomed / (totalCopiesInHand + totalCopiesBottomed)
     */
    @Query(
        """
        SELECT
            cs.scryfall_id                      AS scryfallId,
            COUNT(DISTINCT cs.playtest_session_id) AS testsWithAppearance,
            SUM(cs.copies_in_opening_hand)      AS totalCopiesInHand,
            SUM(cs.copies_bottomed_on_mulligan)  AS totalCopiesBottomed
        FROM playtest_card_stats cs
        INNER JOIN playtest_sessions ps ON ps.id = cs.playtest_session_id
        WHERE ps.deck_id = :deckId
          AND (cs.copies_in_opening_hand > 0 OR cs.copies_bottomed_on_mulligan > 0)
        GROUP BY cs.scryfall_id
        ORDER BY testsWithAppearance DESC
        """
    )
    abstract fun observeCardAppearanceStatsForDeck(deckId: String): Flow<List<PlaytestCardAppearanceRow>>

    // ── Aggregate: per-card stats for a single (deck, card) pair ──────────────

    /**
     * Same as [observeCardAppearanceStatsForDeck] but scoped to one scryfallId.
     * Returns null if the card has never appeared in any saved test for this deck.
     */
    @Query(
        """
        SELECT
            cs.scryfall_id                      AS scryfallId,
            COUNT(DISTINCT cs.playtest_session_id) AS testsWithAppearance,
            SUM(cs.copies_in_opening_hand)      AS totalCopiesInHand,
            SUM(cs.copies_bottomed_on_mulligan)  AS totalCopiesBottomed
        FROM playtest_card_stats cs
        INNER JOIN playtest_sessions ps ON ps.id = cs.playtest_session_id
        WHERE ps.deck_id = :deckId
          AND cs.scryfall_id = :scryfallId
        GROUP BY cs.scryfall_id
        """
    )
    abstract fun observeCardAppearanceStatsForDeckAndCard(
        deckId: String,
        scryfallId: String,
    ): Flow<PlaytestCardAppearanceRow?>

    // ── Aggregate: appearance rate (appeared-in / total tests) ────────────────

    /**
     * Returns appearance rate rows for all cards in [deckId]. The rate is expressed
     * as two raw integers so the consumer controls the division precision:
     *   rate = appearedInCount.toFloat() / totalTests
     *
     * Cards with zero appearances are NOT included (they have no rows in playtest_card_stats).
     * To show a 0% rate for a card, the UI must check against the deck's total-test count.
     */
    @Query(
        """
        SELECT
            cs.scryfall_id                         AS scryfallId,
            COUNT(DISTINCT cs.playtest_session_id) AS appearedInCount,
            (SELECT COUNT(*) FROM playtest_sessions WHERE deck_id = :deckId) AS totalTests
        FROM playtest_card_stats cs
        INNER JOIN playtest_sessions ps ON ps.id = cs.playtest_session_id
        WHERE ps.deck_id = :deckId
          AND cs.copies_in_opening_hand > 0
        GROUP BY cs.scryfall_id
        ORDER BY appearedInCount DESC
        """
    )
    abstract fun observeCardAppearanceRatesForDeck(deckId: String): Flow<List<PlaytestCardRateRow>>

    /**
     * Appearance rate for a single (deck, card) pair.
     */
    @Query(
        """
        SELECT
            cs.scryfall_id                         AS scryfallId,
            COUNT(DISTINCT cs.playtest_session_id) AS appearedInCount,
            (SELECT COUNT(*) FROM playtest_sessions WHERE deck_id = :deckId) AS totalTests
        FROM playtest_card_stats cs
        INNER JOIN playtest_sessions ps ON ps.id = cs.playtest_session_id
        WHERE ps.deck_id = :deckId
          AND cs.scryfall_id = :scryfallId
          AND cs.copies_in_opening_hand > 0
        GROUP BY cs.scryfall_id
        """
    )
    abstract fun observeCardAppearanceRateForDeckAndCard(
        deckId: String,
        scryfallId: String,
    ): Flow<PlaytestCardRateRow?>

    // ── Card-stat row reads ────────────────────────────────────────────────────

    @Query("SELECT * FROM playtest_card_stats WHERE playtest_session_id = :playtestSessionId")
    abstract suspend fun getCardStatsForSession(playtestSessionId: Long): List<PlaytestCardStatEntity>

    // ── Survey reads ───────────────────────────────────────────────────────────

    @Query("SELECT * FROM playtest_survey_answers WHERE playtest_session_id = :playtestSessionId")
    abstract suspend fun getSurveyAnswersForSession(playtestSessionId: Long): List<PlaytestSurveyAnswerEntity>

    @Query("SELECT * FROM playtest_survey_answers WHERE playtest_session_id = :playtestSessionId")
    abstract fun observeSurveyAnswersForSession(playtestSessionId: Long): Flow<List<PlaytestSurveyAnswerEntity>>
}
