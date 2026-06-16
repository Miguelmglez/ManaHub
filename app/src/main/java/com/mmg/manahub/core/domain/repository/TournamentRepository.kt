package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.data.local.entity.TournamentEntity
import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity
import com.mmg.manahub.core.data.local.entity.projection.TournamentStanding
import kotlinx.coroutines.flow.Flow

/**
 * Outcome of the canonical finish-and-advance write path ([TournamentRepository.finishMatch]).
 *
 * Recording a match result, generating the next round, and finishing the tournament are unified into
 * ONE path so that a match played via the game screen advances Swiss/Single-Elim rounds and finishes
 * the tournament exactly like the manual dialog does (audit C1/C2).
 */
sealed interface MatchResultOutcome {
    /** The match was already FINISHED (first-writer-wins guard) — nothing advanced (audit C3). */
    object NoOp : MatchResultOutcome
    /** This finish completed a round and a new round was generated. */
    object RoundGenerated : MatchResultOutcome
    /** The current round still has pending/active matches; nothing advanced yet. */
    object RoundNotComplete : MatchResultOutcome
    /** This finish completed the tournament: status set to FINISHED and completion XP emitted once. */
    object TournamentFinished : MatchResultOutcome
}

interface TournamentRepository {

    suspend fun createTournament(
        name:              String,
        format:            String,
        structure:         String,
        players:           List<Pair<String, String>>,  // (name, hexColor)
        matchesPerPairing: Int,
        isRandomPairings:  Boolean,
    ): Long

    fun observeTournaments(): Flow<List<TournamentEntity>>
    fun observeTournament(tournamentId: Long): Flow<TournamentEntity?>
    fun observeMatches(tournamentId: Long): Flow<List<TournamentMatchEntity>>
    fun observePlayers(tournamentId: Long): Flow<List<TournamentPlayerEntity>>

    suspend fun startTournament(tournamentId: Long)
    suspend fun pauseTournament(tournamentId: Long)
    suspend fun startMatch(matchId: Long)

    /**
     * Canonical finish-and-advance path. First-writer-wins finishes the match, then atomically
     * advances the round / finishes the tournament (emitting completion XP exactly once). Both the
     * game-played flow and the manual dialog route through here so behaviour is identical.
     *
     * @param winnerId null means a draw.
     * @return what happened — see [MatchResultOutcome]. [MatchResultOutcome.NoOp] when the match was
     *   already FINISHED (so callers never double-advance / double-grant).
     */
    suspend fun finishMatch(
        matchId:    Long,
        winnerId:   Long?,
        sessionId:  Long?,
        lifeTotals: Map<Long, Int>,
    ): MatchResultOutcome

    suspend fun resetMatch(matchId: Long)
    suspend fun finishTournament(tournamentId: Long)
    suspend fun calculateStandings(tournamentId: Long): List<TournamentStanding>

    /** @deprecated Use GenerateNextRoundUseCase instead. */
    suspend fun isFinished(tournamentId: Long): Boolean

    suspend fun deleteTournament(tournamentId: Long)
}
