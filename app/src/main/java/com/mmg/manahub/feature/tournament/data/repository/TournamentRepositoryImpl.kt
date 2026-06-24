package com.mmg.manahub.feature.tournament.data.repository

import com.mmg.manahub.core.data.local.dao.TournamentDao
import com.mmg.manahub.core.data.local.entity.TournamentEntity
import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity
import com.mmg.manahub.core.data.local.entity.projection.TournamentStanding
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.feature.tournament.domain.engine.StandingsCalculator
import com.mmg.manahub.feature.tournament.domain.engine.TournamentIdCodec
import com.mmg.manahub.feature.tournament.domain.repository.MatchResultOutcome
import com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository
import com.mmg.manahub.feature.tournament.domain.usecase.GenerateNextRoundUseCase
import com.mmg.manahub.feature.tournament.domain.usecase.NextRoundResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TournamentRepositoryImpl @Inject constructor(
    private val dao: TournamentDao,
    private val progressionEventBus: ProgressionEventBus,
    private val generateNextRound: GenerateNextRoundUseCase,
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
                generateFirstRound(
                    tournamentId     = tournamentId,
                    playerIds        = playerIds,
                    structure        = structure,
                    matchesPerPairing = matchesPerPairing,
                    isRandom         = isRandomPairings,
                )
            },
        )
    }

    // ── First-round match generation ──────────────────────────────────────────

    private fun generateFirstRound(
        tournamentId:      Long,
        playerIds:         List<Long>,
        structure:         String,
        matchesPerPairing: Int,
        isRandom:          Boolean,
    ): List<TournamentMatchEntity> {
        val ordered = if (isRandom) playerIds.shuffled() else playerIds
        return when (structure) {
            "SWISS"       -> generateSwissFirstRound(tournamentId, ordered)
            "SINGLE_ELIM" -> generateSingleElimFirstRound(tournamentId, ordered)
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
     * Swiss round 1: pair consecutive players; the last player gets a bye if count is odd.
     * A bye is stored as a single-player finished match (playerIds = "[id]", winnerId = id).
     */
    private fun generateSwissFirstRound(
        tournamentId: Long,
        playerIds:    List<Long>,
    ): List<TournamentMatchEntity> {
        val matches = mutableListOf<TournamentMatchEntity>()
        var order   = 0
        var i = 0
        while (i + 1 < playerIds.size) {
            matches.add(
                TournamentMatchEntity(
                    tournamentId   = tournamentId,
                    round          = 1,
                    playerIds      = "[${playerIds[i]},${playerIds[i + 1]}]",
                    scheduledOrder = order++,
                )
            )
            i += 2
        }
        if (i < playerIds.size) {
            // Odd player out → bye (auto-finished, 3 points)
            matches.add(
                TournamentMatchEntity(
                    tournamentId   = tournamentId,
                    round          = 1,
                    playerIds      = "[${playerIds[i]}]",
                    winnerId       = playerIds[i],
                    status         = "FINISHED",
                    scheduledOrder = order,
                )
            )
        }
        return matches
    }

    /**
     * Single-elimination round 1: seed to nearest power of 2.
     * Top seeds get byes (auto-finished); remaining players pair consecutively.
     */
    private fun generateSingleElimFirstRound(
        tournamentId: Long,
        playerIds:    List<Long>,
    ): List<TournamentMatchEntity> {
        val matches = mutableListOf<TournamentMatchEntity>()
        var order   = 0

        val nextPow2 = nextPowerOf2(playerIds.size)
        val byeCount = nextPow2 - playerIds.size

        var byesAssigned = 0
        val playing = mutableListOf<Long>()

        for (player in playerIds) {
            if (byesAssigned < byeCount) {
                matches.add(
                    TournamentMatchEntity(
                        tournamentId   = tournamentId,
                        round          = 1,
                        playerIds      = "[$player]",
                        winnerId       = player,
                        status         = "FINISHED",
                        scheduledOrder = order++,
                    )
                )
                byesAssigned++
            } else {
                playing.add(player)
            }
        }

        var j = 0
        while (j + 1 < playing.size) {
            matches.add(
                TournamentMatchEntity(
                    tournamentId   = tournamentId,
                    round          = 1,
                    playerIds      = "[${playing[j]},${playing[j + 1]}]",
                    scheduledOrder = order++,
                )
            )
            j += 2
        }
        if (j < playing.size) {
            matches.add(
                TournamentMatchEntity(
                    tournamentId   = tournamentId,
                    round          = 1,
                    playerIds      = "[${playing[j]}]",
                    winnerId       = playing[j],
                    status         = "FINISHED",
                    scheduledOrder = order,
                )
            )
        }
        return matches
    }

    // ── Standings ─────────────────────────────────────────────────────────────

    override suspend fun calculateStandings(tournamentId: Long): List<TournamentStanding> =
        withContext(ioDispatcher) {
            val players         = dao.getPlayers(tournamentId)
            val finishedMatches = dao.getFinishedMatches(tournamentId)
            StandingsCalculator.calculate(players, finishedMatches)
        }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override suspend fun startTournament(tournamentId: Long) = withContext(ioDispatcher) {
        dao.updateStatus(tournamentId, "ACTIVE")
    }

    override suspend fun pauseTournament(tournamentId: Long) = withContext(ioDispatcher) {
        dao.updateStatus(tournamentId, "PAUSED")
    }

    override suspend fun startMatch(matchId: Long) = withContext(ioDispatcher) {
        dao.startMatch(matchId)
    }

    override suspend fun finishMatch(
        matchId:    Long,
        winnerId:   Long?,
        sessionId:  Long?,
        lifeTotals: Map<Long, Int>,
    ): MatchResultOutcome = withContext(ioDispatcher) {
        val match = dao.getMatchById(matchId)
            ?: throw IllegalArgumentException("Match $matchId not found")

        if (winnerId != null) {
            val participantIds = parsePlayerIds(match.playerIds).toSet()
            require(winnerId in participantIds) {
                "winnerId $winnerId is not a participant of match $matchId (participants: $participantIds)"
            }
        }

        // Tournament + players are read once; they do not change during a single finish, and the
        // advancement decision below is PURE (no DB access inside the transaction lambda).
        val tournamentId = match.tournamentId
        val tournament = dao.getTournamentById(tournamentId)
            ?: throw IllegalArgumentException("Tournament $tournamentId not found for match $matchId")
        val players = dao.getPlayers(tournamentId)

        val json = TournamentIdCodec.encodeLifeTotals(lifeTotals)

        // Single canonical write path (audit C1/C2/C3): first-writer-wins finish + round advancement
        // + tournament-finish stamp, all atomic. The lambda only runs when the guarded finish actually
        // won the race (0 rows → NO_OP), so a repeated/concurrent finish never double-advances.
        val kind = dao.finishMatchAndAdvanceAtomically(
            tournamentId = tournamentId,
            matchId      = matchId,
            winnerId     = winnerId,
            sessionId    = sessionId,
            lifeTotals   = json,
            finishedAt   = System.currentTimeMillis(),
            buildAdvancement = { matchesAfterFinish ->
                val plan = generateNextRound.plan(tournament, players, matchesAfterFinish)
                val advanceKind = when {
                    plan.tournamentFinished                      -> TournamentDao.AdvanceKind.TOURNAMENT_FINISHED
                    plan.result is NextRoundResult.RoundGenerated -> TournamentDao.AdvanceKind.ROUND_GENERATED
                    else                                         -> TournamentDao.AdvanceKind.ROUND_NOT_COMPLETE
                }
                plan.matchesToInsert to advanceKind
            },
        )

        when (kind) {
            TournamentDao.AdvanceKind.NO_OP               -> MatchResultOutcome.NoOp
            TournamentDao.AdvanceKind.ROUND_NOT_COMPLETE  -> MatchResultOutcome.RoundNotComplete
            TournamentDao.AdvanceKind.ROUND_GENERATED     -> MatchResultOutcome.RoundGenerated
            TournamentDao.AdvanceKind.TOURNAMENT_FINISHED -> {
                // Emit completion XP AFTER the transaction commits (ADR-002 §1). The atomic finish ran
                // exactly once (guard), so this fires once per real completion. The TournamentCompleted
                // idempotency key is tournament-scoped + device-scoped (ProgressionEvent §L3), so even if
                // finishTournament is somehow reached again the ledger dedupes the grant.
                emitTournamentCompleted(tournamentId, tournament.structure)
                MatchResultOutcome.TournamentFinished
            }
        }
    }

    override suspend fun resetMatch(matchId: Long) = withContext(ioDispatcher) {
        dao.resetMatchToPending(matchId)
    }

    override suspend fun finishTournament(tournamentId: Long) = withContext(ioDispatcher) {
        // Double-emit guard (audit regression guard): only stamp + emit when the tournament is NOT
        // already FINISHED. The canonical finish-and-advance path stamps FINISHED inside its
        // transaction and emits XP itself; this public method is the fallback/standalone caller. If
        // the tournament is already FINISHED we no-op so completion XP is emitted at most once per
        // tournament (and the ledger key would dedupe a second grant anyway, ProgressionEvent §L3).
        val tournament = dao.getTournamentById(tournamentId)
        if (tournament == null || tournament.status == "FINISHED") return@withContext

        dao.finishTournament(tournamentId, System.currentTimeMillis())
        emitTournamentCompleted(tournamentId, tournament.structure)
    }

    /**
     * Emits [ProgressionEvent.TournamentCompleted] after a finish commit (ADR-002 §1). `type` is the
     * tournament structure.
     *
     * isLocalWinner: tournaments have NO per-seat "local"/"app user" flag (unlike game sessions'
     * PlayerSessionEntity.isLocal). There is therefore no reliable way to know whether the app's user
     * won — name matching is forbidden (see memory feedback_survey_winloss_isLocal). We pass false so
     * the base "tournament completed" XP still grants, but the "tournament won" bonus does not. Wiring
     * the won-bonus requires a local-seat concept on tournaments (schema + setup UI) and is deferred.
     *
     * The TournamentCompleted idempotency key is `tournament:{id}` and device-scoped (ProgressionEvent
     * §L3), so even if this is reached more than once for the same tournament the XP ledger grants the
     * completion bonus exactly once.
     */
    private suspend fun emitTournamentCompleted(tournamentId: Long, structure: String) {
        progressionEventBus.emit(
            ProgressionEvent.TournamentCompleted(
                tournamentId = tournamentId,
                type = structure,
                isLocalWinner = false,
                occurredAt = Clock.System.now(),
            )
        )
    }

    override suspend fun isFinished(tournamentId: Long): Boolean = withContext(ioDispatcher) {
        val finished = dao.getFinishedMatches(tournamentId)
        val pending  = dao.getPendingMatchCount(tournamentId)
        finished.isNotEmpty() && pending == 0
    }

    override suspend fun deleteTournament(tournamentId: Long) = withContext(ioDispatcher) {
        dao.deleteTournament(tournamentId)
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

    private fun parsePlayerIds(json: String): List<Long> = TournamentIdCodec.decodeIds(json)

    private fun nextPowerOf2(n: Int): Int {
        var pow = 1
        while (pow < n) pow = pow shl 1
        return pow
    }
}
