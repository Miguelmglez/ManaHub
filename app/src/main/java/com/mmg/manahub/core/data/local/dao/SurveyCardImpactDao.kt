package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mmg.manahub.core.data.local.entity.SurveyCardImpactEntity
import kotlinx.coroutines.flow.Flow

/**
 * Aggregate projection: how many times a card was flagged MVP for a given deck.
 */
data class CardImpactCountRow(
    val cardId:   String,
    val mvpCount: Int,
)

/**
 * Data-access for per-seat card-impact ratings (see [SurveyCardImpactEntity], ADR-001).
 *
 * Follows the project `abstract class` DAO convention so that compound write paths can
 * be expressed as `@Transaction` methods when needed. Inserts use the default ABORT
 * conflict strategy — impacts are append-only per session and callers delete-then-insert
 * via [deleteForSession] + [insertAll] when re-saving.
 */
@Dao
abstract class SurveyCardImpactDao {

    // ── Write ────────────────────────────────────────────────────────────────────

    /** Inserts all [impacts]. Callers should [deleteForSession] first when re-saving. */
    @Insert
    abstract suspend fun insertAll(impacts: List<SurveyCardImpactEntity>)

    /** Removes every impact row for [sessionId]. */
    @Query("DELETE FROM survey_card_impacts WHERE session_id = :sessionId")
    abstract suspend fun deleteForSession(sessionId: Long)

    // ── Read ─────────────────────────────────────────────────────────────────────

    /** Streams all impact rows for [sessionId]. */
    @Query("SELECT * FROM survey_card_impacts WHERE session_id = :sessionId")
    abstract fun observeForSession(sessionId: Long): Flow<List<SurveyCardImpactEntity>>

    /**
     * Streams the top MVP-flagged cards for a deck, ordered by MVP count descending.
     *
     * Joins through `player_sessions` (the seat) to resolve the seat's `deck_id`,
     * then counts MVP impacts per card. Bye/opponent seats are naturally excluded
     * because the deck filter only matches seats that played [deckId].
     */
    @Query(
        """
        SELECT sci.card_id AS cardId,
               COUNT(*)     AS mvpCount
        FROM survey_card_impacts sci
        INNER JOIN player_sessions ps ON ps.id = sci.player_session_id
        WHERE ps.deck_id = :deckId
          AND sci.impact = 'MVP'
        GROUP BY sci.card_id
        ORDER BY mvpCount DESC
        """
    )
    abstract fun observeTopMvpCardsForDeck(deckId: String): Flow<List<CardImpactCountRow>>
}
