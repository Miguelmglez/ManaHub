package com.mmg.manahub.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * One-shot (suspend) SNAPSHOT reads of existing Room tables for Family-A achievement resolvers
 * (ADR-002 §4) and the Family-A backfill.
 *
 * Separate from [GamificationDao] (which owns the gamification entities) because these are pure READS
 * over the collection / game / deck / survey tables — they do not touch any gamification table and add
 * NO schema (only `@Query` methods, no new entities). The [AchievementEvaluator] maps each
 * [com.mmg.manahub.core.gamification.domain.catalog.AchievementResolver] to one method here.
 *
 * ### Win semantics (CRITICAL)
 * Every win-based count resolves against the local seat (`player_sessions.is_local = 1`), NEVER a
 * `winnerName == playerName` match — see ADR-002 §7 and memory `feedback_survey_winloss_isLocal`.
 *
 * All counts COALESCE to 0 so an empty table returns 0, never null.
 */
@Dao
interface GamificationStatsDao {

    // ── Collection ───────────────────────────────────────────────────────────────

    /** Total quantity of owned cards (sum of quantity over non-deleted rows). */
    @Query(
        """
        SELECT COALESCE(SUM(quantity), 0) FROM user_card_collection
        WHERE is_deleted = 0
        """
    )
    suspend fun totalCardsOwned(): Int

    /** Distinct owned printings (rows with quantity > 0, not deleted). */
    @Query(
        """
        SELECT COUNT(*) FROM user_card_collection
        WHERE is_deleted = 0 AND quantity > 0
        """
    )
    suspend fun uniqueCardsOwned(): Int

    /** Distinct owned foil printings. */
    @Query(
        """
        SELECT COUNT(*) FROM user_card_collection
        WHERE is_deleted = 0 AND quantity > 0 AND is_foil = 1
        """
    )
    suspend fun foilCardsOwned(): Int

    /** Count of owned mythic printings (joins the cards table for rarity). */
    @Query(
        """
        SELECT COUNT(*) FROM user_card_collection uc
        INNER JOIN cards c ON c.scryfall_id = uc.scryfall_id
        WHERE uc.is_deleted = 0 AND uc.quantity > 0 AND c.rarity = 'mythic'
        """
    )
    suspend fun mythicCardsOwned(): Int

    /** Highest single-card USD price across owned cards, or 0.0 if none/null. */
    @Query(
        """
        SELECT COALESCE(MAX(c.price_usd), 0.0) FROM user_card_collection uc
        INNER JOIN cards c ON c.scryfall_id = uc.scryfall_id
        WHERE uc.is_deleted = 0 AND uc.quantity > 0
        """
    )
    suspend fun maxCardValueUsd(): Double

    /**
     * Number of owned distinct printings whose `cards.colors` JSON array contains [colorToken]
     * (e.g. `"W"`). Colors are stored as a JSON array string like `["W","U"]`, so a LIKE on the
     * quoted token is the cheapest reliable match. Counts toward the "colors with ≥20 cards" resolver.
     */
    @Query(
        """
        SELECT COUNT(*) FROM user_card_collection uc
        INNER JOIN cards c ON c.scryfall_id = uc.scryfall_id
        WHERE uc.is_deleted = 0 AND uc.quantity > 0
          AND c.colors LIKE '%"' || :colorToken || '"%'
        """
    )
    suspend fun ownedCountForColor(colorToken: String): Int

    // ── Games (local-seat semantics) ──────────────────────────────────────────────

    /** Total games logged. */
    @Query("SELECT COUNT(*) FROM game_sessions")
    suspend fun totalGames(): Int

    /** Wins by the local seat. */
    @Query(
        """
        SELECT COUNT(*) FROM game_sessions gs
        INNER JOIN player_sessions ps ON ps.sessionId = gs.id
        WHERE ps.is_local = 1 AND ps.isWinner = 1
        """
    )
    suspend fun localWins(): Int

    /** Local wins that ended in <= [maxTurns] turns (fast/aggro wins). */
    @Query(
        """
        SELECT COUNT(*) FROM game_sessions gs
        INNER JOIN player_sessions ps ON ps.sessionId = gs.id
        WHERE ps.is_local = 1 AND ps.isWinner = 1 AND gs.totalTurns <= :maxTurns
        """
    )
    suspend fun quickLocalWins(maxTurns: Int): Int

    /** Local wins where the local seat finished at <= [maxLife] life (comeback wins). */
    @Query(
        """
        SELECT COUNT(*) FROM game_sessions gs
        INNER JOIN player_sessions ps ON ps.sessionId = gs.id
        WHERE ps.is_local = 1 AND ps.isWinner = 1 AND ps.finalLife <= :maxLife
        """
    )
    suspend fun comebackLocalWins(maxLife: Int): Int

    /** Games whose local seat finished at EXACTLY [life] life (used by the 1-life secret). */
    @Query(
        """
        SELECT COUNT(*) FROM game_sessions gs
        INNER JOIN player_sessions ps ON ps.sessionId = gs.id
        WHERE ps.is_local = 1 AND ps.isWinner = 1 AND ps.finalLife = :life
        """
    )
    suspend fun localWinsAtExactLife(life: Int): Int

    /** Games lasting >= [minDurationMs]. */
    @Query("SELECT COUNT(*) FROM game_sessions WHERE durationMs >= :minDurationMs")
    suspend fun marathonGames(minDurationMs: Long): Int

    /** Local wins in COMMANDER mode. */
    @Query(
        """
        SELECT COUNT(*) FROM game_sessions gs
        INNER JOIN player_sessions ps ON ps.sessionId = gs.id
        WHERE ps.is_local = 1 AND ps.isWinner = 1 AND gs.mode = 'COMMANDER'
        """
    )
    suspend fun commanderLocalWins(): Int

    /** Games with [minPlayers] or more players. */
    @Query("SELECT COUNT(*) FROM game_sessions WHERE playerCount >= :minPlayers")
    suspend fun multiplayerGames(minPlayers: Int): Int

    // ── Decks ──────────────────────────────────────────────────────────────────────

    /** Non-deleted deck count. */
    @Query("SELECT COUNT(*) FROM decks WHERE is_deleted = 0")
    suspend fun decksBuilt(): Int

    /** Number of distinct deck formats across non-deleted decks. */
    @Query("SELECT COUNT(DISTINCT format) FROM decks WHERE is_deleted = 0")
    suspend fun distinctDeckFormats(): Int

    // ── Surveys ──────────────────────────────────────────────────────────────────────

    /** Distinct sessions with at least one survey answer. */
    @Query("SELECT COUNT(DISTINCT sessionId) FROM survey_answers")
    suspend fun surveysCompleted(): Int
}
