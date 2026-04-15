package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.dao.TournamentDao
import com.mmg.manahub.core.data.local.entity.TournamentEntity
import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity
import com.mmg.manahub.core.data.local.entity.projection.TournamentStanding
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.repository.TournamentRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TournamentRepositoryImpl @Inject constructor(
    private val dao: TournamentDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TournamentRepository {

    // ── Creation ──────────────────────────────────────────────────────────────

    override suspend fun createTournament(
        name:              String,
        format:            String,
        structure:         String,
        players:           List<Pair<String, String>>,
        matchesPerPairing: Int,
        isRandomPairings:  Boolean,
    ): Long = withContext(ioDispatcher) {
        val tournament = TournamentEntity(
            name              = name,
            format            = format,
            structure         = structure,
            status            = "SETUP",
            matchesPerPairing = matchesPerPairing,
            isRandomPairings  = isRandomPairings,
        )
        val playerEntities = players.mapIndexed { i, (pName, color) ->
            TournamentPlayerEntity(
                tournamentId = 0L,  // set atomically inside insertTournamentAtomically
                playerName   = pName,
                playerColor  = color,
                seed         = i,
            )
        }
        dao.insertTournamentAtomically(
            tournament   = tournament,
            players      = playerEntities,
            buildMatches = { tournamentId, playerIds ->
                generateMatches(
                    tournamentId      = tournamentId,
                    playerIds         = playerIds,
                    structure         = structure,
                    matchesPerPairing = matchesPerPairing,
                    isRandom          = isRandomPairings,
                )
            },
        )
    }

    // ── Match generation ──────────────────────────────────────────────────────

    private fun generateMatches(
        tournamentId:      Long,
        playerIds:         List<Long>,
        structure:         String,
        matchesPerPairing: Int,
        isRandom:          Boolean,
    ): List<TournamentMatchEntity> {
        val ordered = if (isRandom) playerIds.shuffled() else playerIds
        return when (structure) {
            "SWISS"       -> generatePairedFirstRound(tournamentId, ordered)
            "SINGLE_ELIM" -> generatePairedFirstRound(tournamentId, ordered)
            else          -> generateRoundRobin(tournamentId, ordered, matchesPerPairing)
        }
    }

    private fun generateRoundRobin(
        tournamentId:      Long,
        playerIds:         List<Long>,
        matchesPerPairing: Int,
    ): List<TournamentMatchEntity> {
        val matches = mutableListOf<TournamentMatchEntity>()
        var order = 0
        for (i in playerIds.indices) {
            for (j in i + 1 until playerIds.size) {
                repeat(matchesPerPairing) {
                    matches.add(
                        TournamentMatchEntity(
                            tournamentId   = tournamentId,
                            round          = 1,
                            playerIds      = "[${playerIds[i]},${playerIds[j]}]",
                            scheduledOrder = order++,
                        )
                    )
                }
            }
        }
        return matches
    }

    /**
     * Pairs consecutive players for the first round.
     *
     * Used by both Swiss (first round only) and Single Elimination brackets.
     * Players are already ordered/shuffled by [generateMatches] before reaching this method.
     */
    private fun generatePairedFirstRound(
        tournamentId: Long,
        playerIds:    List<Long>,
    ): List<TournamentMatchEntity> {
        val matches = mutableListOf<TournamentMatchEntity>()
        var order   = 0
        for (i in playerIds.indices step 2) {
            if (i + 1 < playerIds.size) {
                matches.add(
                    TournamentMatchEntity(
                        tournamentId   = tournamentId,
                        round          = 1,
                        playerIds      = "[${playerIds[i]},${playerIds[i + 1]}]",
                        scheduledOrder = order++,
                    )
                )
            }
        }
        return matches
    }

    // ── Standings ─────────────────────────────────────────────────────────────

    override suspend fun calculateStandings(tournamentId: Long): List<TournamentStanding> = withContext(ioDispatcher) {
        val players  = dao.getPlayers(tournamentId)
        val finished = dao.getFinishedMatches(tournamentId)

        players.map { player ->
            var wins      = 0
            var losses    = 0
            var lifeTotal = 0

            finished.forEach { match ->
                val ids = parsePlayerIds(match.playerIds)
                if (player.id in ids) {
                    lifeTotal += parseLifeTotals(match.finalLifeTotals)[player.id] ?: 0
                    when {
                        match.winnerId == player.id -> wins++
                        match.winnerId != null       -> losses++
                    }
                }
            }

            TournamentStanding(
                player        = player,
                wins          = wins,
                losses        = losses,
                draws         = 0,
                points        = wins * 3,
                lifeTotal     = lifeTotal,
                matchesPlayed = wins + losses,
                position      = 0,
            )
        }
            .sortedWith(
                compareByDescending<TournamentStanding> { it.points }
                    .thenByDescending { it.lifeTotal }
                    .thenByDescending { it.wins }
            )
            .mapIndexed { index, standing -> standing.copy(position = index + 1) }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override suspend fun startTournament(tournamentId: Long) = withContext(ioDispatcher) {
        dao.updateStatus(tournamentId, "ACTIVE")
    }

    override suspend fun startMatch(matchId: Long) = withContext(ioDispatcher) {
        dao.startMatch(matchId)
    }

    override suspend fun finishMatch(
        matchId:    Long,
        winnerId:   Long,
        sessionId:  Long?,
        lifeTotals: Map<Long, Int>,
    ) = withContext(ioDispatcher) {
        val json = lifeTotals.entries
            .joinToString(",", "{", "}") { "${it.key}:${it.value}" }
        dao.finishMatch(matchId, winnerId, sessionId, json)
    }

    override suspend fun finishTournament(tournamentId: Long) = withContext(ioDispatcher) {
        dao.finishTournament(tournamentId, System.currentTimeMillis())
    }

    override suspend fun isFinished(tournamentId: Long): Boolean = withContext(ioDispatcher) {
        // A tournament with 0 matches must NOT be considered finished.
        // getPendingMatchCount == 0 is vacuously true on an empty match list,
        // which would cause finishTournament to be called immediately after creation.
        val finished = dao.getFinishedMatches(tournamentId)
        val pending  = dao.getPendingMatchCount(tournamentId)
        finished.isNotEmpty() && pending == 0
    }

    // ── Observe ───────────────────────────────────────────────────────────────

    override fun observeTournaments(): Flow<List<TournamentEntity>> =
        dao.observeAllTournaments()

    override fun observeTournament(tournamentId: Long): Flow<TournamentEntity?> =
        dao.observeTournament(tournamentId)

    override fun observeMatches(tournamentId: Long): Flow<List<TournamentMatchEntity>> =
        dao.observeMatches(tournamentId)

    override fun observePlayers(tournamentId: Long): Flow<List<TournamentPlayerEntity>> =
        dao.observePlayers(tournamentId)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parsePlayerIds(json: String): List<Long> =
        json.trim('[', ']').split(",").mapNotNull { it.trim().toLongOrNull() }

    private fun parseLifeTotals(json: String): Map<Long, Int> {
        if (json.isBlank()) return emptyMap()
        return json.trim('{', '}').split(",").mapNotNull { entry ->
            // Use indexOf(':') instead of split(":") so that negative life values
            // (e.g. "101:-3") are correctly parsed — split would produce 3 parts
            // and the old size==2 guard would silently drop the entry.
            val colonIdx = entry.indexOf(':')
            if (colonIdx == -1) return@mapNotNull null
            val id   = entry.substring(0, colonIdx).trim().toLongOrNull()
            val life = entry.substring(colonIdx + 1).trim().toIntOrNull()
            if (id != null && life != null) id to life else null
        }.toMap()
    }
}
