package com.mmg.manahub.feature.game.domain.repository

import com.mmg.manahub.feature.game.domain.model.ArchetypeMatchupData
import com.mmg.manahub.feature.game.domain.model.DeckStats
import com.mmg.manahub.feature.game.domain.model.EliminationStats
import com.mmg.manahub.feature.game.domain.model.GameModeCount
import com.mmg.manahub.feature.game.domain.model.GameSessionData
import com.mmg.manahub.feature.game.domain.model.SessionDetail
import com.mmg.manahub.feature.game.domain.model.SessionHistoryEntry
import kotlinx.coroutines.flow.Flow

/**
 * Contract for persisting and querying finished game sessions.
 *
 * All return types are pure domain models from :shared:core-model — zero Room / AndroidX
 * dependencies allowed in this interface. The Android implementation maps DAO projection
 * types to these domain types inside
 * [com.mmg.manahub.feature.game.data.repository.GameSessionRepositoryImpl].
 *
 * Interface moved from :app to :shared:core-domain (KMP Phase 4) to unblock the web target;
 * package name preserved so no consumer import changes.
 */
interface GameSessionRepository {

    /**
     * Persists a finished game and emits a
     * [com.mmg.manahub.core.gamification.domain.event.ProgressionEvent.GameFinished].
     * Returns the new session id.
     */
    suspend fun saveGameSession(data: GameSessionData): Long

    /** Returns the full detail (session + players) for a given session id, or null if not found. */
    suspend fun getSessionById(sessionId: Long): SessionDetail?

    /** Emits the [limit] most-recent sessions, most-recent first. */
    fun observeRecentSessions(limit: Int = 10): Flow<List<SessionDetail>>

    /** Total number of finished sessions. */
    fun observeTotalGames(): Flow<Int>

    /** Count of sessions won by [playerName] (name-match, legacy). Prefer [observeLocalWins]. */
    fun observeWins(playerName: String): Flow<Int>

    /** Count of sessions won by the local seat (`is_local = 1`); name-agnostic. */
    fun observeLocalWins(): Flow<Int>

    /** Session history resolved against the local seat, most-recent first. */
    fun observeLocalSessionHistory(limit: Int = 50): Flow<List<SessionHistoryEntry>>

    fun observeAvgLifeOnWin(): Flow<Double?>

    fun observeAvgLifeOnLoss(): Flow<Double?>

    /** Per-deck win/loss aggregated from the local seat's deck associations. */
    fun observeDeckStats(): Flow<List<DeckStats>>

    /** The most-played game mode (by count). */
    fun observeFavoriteMode(): Flow<GameModeCount?>

    fun observeAvgDurationMs(): Flow<Double?>

    /** The elimination reason that most frequently ended the local seat's games. */
    fun observeMostFrequentElimination(): Flow<EliminationStats?>

    fun observeAvgWinTurn(playerName: String): Flow<Double?>

    fun observeCurrentStreak(playerName: String): Flow<Int>

    /** Count of sessions with PENDING or PARTIAL survey status. */
    fun observePendingSurveyCount(): Flow<Int>

    /** Per-deck win/loss stats aggregated from the local seat's sessions. */
    fun observeLocalDeckGameStats(): Flow<List<DeckStats>>

    /** Win-rate breakdown grouped by the opponent's classified archetype. */
    fun observeArchetypeMatchups(): Flow<List<ArchetypeMatchupData>>

    suspend fun deleteSession(sessionId: Long)
}
