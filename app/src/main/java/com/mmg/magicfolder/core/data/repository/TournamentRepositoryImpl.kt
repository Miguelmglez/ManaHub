package com.mmg.magicfolder.core.data.repository

import com.mmg.magicfolder.core.data.local.dao.TournamentDao
import com.mmg.magicfolder.core.data.local.entity.TournamentEntity
import com.mmg.magicfolder.core.data.local.entity.TournamentMatchEntity
import com.mmg.magicfolder.core.data.local.entity.TournamentPlayerEntity
import com.mmg.magicfolder.core.data.local.entity.projection.TournamentStanding
import com.mmg.magicfolder.core.domain.repository.TournamentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TournamentRepositoryImpl @Inject constructor(
    private val dao: TournamentDao,
) : TournamentRepository {

    // ── Creation ──────────────────────────────────────────────────────────────

    override suspend fun createTournament(
        name:              String,
        format:            String,
        structure:         String,
        players:           List<Pair<String, String>>,
        matchesPerPairing: Int,
        isRandomPairings:  Boolean,
    ): Long {
        val tournamentId = dao.insertTournament(
            TournamentEntity(
                name              = name,
                format            = format,
                structure         = structure,
                status            = "SETUP",
                matchesPerPairing = matchesPerPairing,
                isRandomPairings  = isRandomPairings,
            )
        )

        val playerEntities = players.mapIndexed { i, (pName, color) ->
            TournamentPlayerEntity(
                tournamentId = tournamentId,
                playerName   = pName,
                playerColor  = color,
                seed         = i,
            )
        }
        val playerIds = dao.insertPlayers(playerEntities)

        val matches = generateMatches(
            tournamentId      = tournamentId,
            playerIds         = playerIds,
            structure         = structure,
            matchesPerPairing = matchesPerPairing,
            isRandom          = isRandomPairings,
        )
        dao.insertMatches(matches)

        return tournamentId
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
            "SWISS"       -> generateFirstSwissRound(tournamentId, ordered)
            "SINGLE_ELIM" -> generateSingleElimination(tournamentId, ordered)
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

    private fun generateFirstSwissRound(
        tournamentId: Long,
        playerIds:    List<Long>,
    ): List<TournamentMatchEntity> {
        val shuffled = playerIds.shuffled()
        val matches  = mutableListOf<TournamentMatchEntity>()
        var order    = 0
        for (i in shuffled.indices step 2) {
            if (i + 1 < shuffled.size) {
                matches.add(
                    TournamentMatchEntity(
                        tournamentId   = tournamentId,
                        round          = 1,
                        playerIds      = "[${shuffled[i]},${shuffled[i + 1]}]",
                        scheduledOrder = order++,
                    )
                )
            }
        }
        return matches
    }

    private fun generateSingleElimination(
        tournamentId: Long,
        playerIds:    List<Long>,
    ): List<TournamentMatchEntity> {
        val shuffled = playerIds.shuffled()
        val matches  = mutableListOf<TournamentMatchEntity>()
        var order    = 0
        for (i in shuffled.indices step 2) {
            if (i + 1 < shuffled.size) {
                matches.add(
                    TournamentMatchEntity(
                        tournamentId   = tournamentId,
                        round          = 1,
                        playerIds      = "[${shuffled[i]},${shuffled[i + 1]}]",
                        scheduledOrder = order++,
                    )
                )
            }
        }
        return matches
    }

    // ── Standings ─────────────────────────────────────────────────────────────

    override suspend fun calculateStandings(tournamentId: Long): List<TournamentStanding> {
        val players  = dao.getPlayers(tournamentId)
        val finished = dao.getFinishedMatches(tournamentId)

        return players.map { player ->
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

    override suspend fun startTournament(tournamentId: Long) =
        dao.updateStatus(tournamentId, "ACTIVE")

    override suspend fun startMatch(matchId: Long) =
        dao.startMatch(matchId)

    override suspend fun finishMatch(
        matchId:    Long,
        winnerId:   Long,
        sessionId:  Long?,
        lifeTotals: Map<Long, Int>,
    ) {
        val json = lifeTotals.entries
            .joinToString(",", "{", "}") { "${it.key}:${it.value}" }
        dao.finishMatch(matchId, winnerId, sessionId, json)
    }

    override suspend fun finishTournament(tournamentId: Long) =
        dao.finishTournament(tournamentId, System.currentTimeMillis())

    override suspend fun isFinished(tournamentId: Long): Boolean =
        dao.getPendingMatchCount(tournamentId) == 0

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
            val parts = entry.split(":")
            if (parts.size == 2) {
                val id   = parts[0].trim().toLongOrNull()
                val life = parts[1].trim().toIntOrNull()
                if (id != null && life != null) id to life else null
            } else null
        }.toMap()
    }
}
