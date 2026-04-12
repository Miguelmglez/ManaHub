package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.data.local.entity.TournamentEntity
import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity
import com.mmg.manahub.core.data.local.entity.projection.TournamentStanding
import kotlinx.coroutines.flow.Flow

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
    suspend fun startMatch(matchId: Long)
    suspend fun finishMatch(
        matchId:    Long,
        winnerId:   Long,
        sessionId:  Long?,
        lifeTotals: Map<Long, Int>,
    )
    suspend fun finishTournament(tournamentId: Long)
    suspend fun calculateStandings(tournamentId: Long): List<TournamentStanding>
    suspend fun isFinished(tournamentId: Long): Boolean
}
