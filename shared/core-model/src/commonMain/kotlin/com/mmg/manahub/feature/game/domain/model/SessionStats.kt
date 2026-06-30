package com.mmg.manahub.feature.game.domain.model

/** Domain equivalent of [com.mmg.manahub.core.data.local.dao.DeckStatsRow]. */
data class DeckStats(
    val deckId: String?,
    val deckName: String?,
    val totalGames: Int,
    val wins: Int,
)

/** Domain equivalent of [com.mmg.manahub.core.data.local.dao.ModeCount]. */
data class GameModeCount(val mode: String, val count: Int)

/** Domain equivalent of [com.mmg.manahub.core.data.local.dao.EliminationCount]. */
data class EliminationStats(val eliminationReason: String, val count: Int)

/**
 * Domain equivalent of [com.mmg.manahub.core.data.local.dao.LocalSessionHistoryRow].
 *
 * Win/loss is resolved against the local seat (`is_local = 1`), never a playerName match
 * (see ADR-001 and memory feedback_survey_winloss_isLocal).
 */
data class SessionHistoryEntry(
    val sessionId: Long,
    val mode: String,
    val totalTurns: Int,
    val durationMs: Long,
    val playedAt: Long,
    val winnerName: String,
    val surveyStatus: String,
    val localIsWinner: Boolean,
    val localDeckId: String?,
    val localDeckName: String?,
)

/** Summary projection of a game session (no players). */
data class SessionSummaryData(
    val id: Long,
    val playedAt: Long,
    val durationMs: Long,
    val mode: String,
    val totalTurns: Int,
    val playerCount: Int,
    val winnerName: String,
    val surveyStatus: String,
)

/** Per-seat projection within a session. */
data class PlayerSummaryData(
    val id: Long,
    val sessionId: Long,
    val playerId: Int,
    val playerName: String,
    val finalLife: Int,
    val finalPoison: Int,
    val eliminationReason: String?,
    val commanderDamageDealt: Int,
    val commanderDamageReceived: Int,
    val deckId: String?,
    val deckName: String?,
    val isWinner: Boolean,
    val isLocal: Boolean,
    val archetype: String?,
)

/** Domain equivalent of [com.mmg.manahub.core.data.local.dao.ArchetypeMatchupRow]. */
data class ArchetypeMatchupData(
    val opponentArchetype: String,
    val totalGames: Int,
    val wins: Int,
)

/**
 * Domain equivalent of [com.mmg.manahub.core.data.local.entity.GameSessionWithPlayers].
 * Combines a session summary with its player list.
 */
data class SessionDetail(
    val session: SessionSummaryData,
    val players: List<PlayerSummaryData>,
)
