package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.dao.TournamentDao
import com.mmg.manahub.core.data.local.entity.TournamentEntity
import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity
import com.mmg.manahub.core.data.local.entity.projection.TournamentStanding
import com.mmg.manahub.feature.tournament.domain.repository.MatchResultOutcome
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.feature.tournament.data.repository.TournamentRepositoryImpl
import com.mmg.manahub.feature.tournament.domain.usecase.GenerateNextRoundUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TournamentRepositoryImpl].
 *
 * REGRESSION GROUP A: "createTournament @Transaction fix"
 * Before the fix, createTournament() called insertTournament, insertPlayers, and insertMatches
 * as three separate DAO calls. A crash between any pair would leave a partially-created tournament.
 * Fix: new insertTournamentAtomically() @Transaction method with a buildMatches lambda.
 * These tests verify the single-call contract and that the old separate methods are NOT called.
 *
 * REGRESSION GROUP B: "double shuffle bug"
 * generateSingleElimination() and generateFirstSwissRound() previously shuffled their input
 * playerIds again internally, even though generateMatches() already shuffled when isRandom=true.
 * Fix: removed the inner shuffles. The methods now trust their already-ordered input.
 * These tests verify deterministic ordering when isRandomPairings=false.
 */
class TournamentRepositoryImplTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val dao = mockk<TournamentDao>(relaxed = true)
    private val progressionEventBus = mockk<ProgressionEventBus>(relaxed = true)
    private val generateNextRound = mockk<GenerateNextRoundUseCase>(relaxed = true)
    private lateinit var repository: TournamentRepositoryImpl

    // ── Fixture helpers ───────────────────────────────────────────────────────

    private fun buildPlayer(
        id:           Long   = 0L,
        tournamentId: Long   = 1L,
        name:         String = "Player",
        seed:         Int    = 0,
    ) = TournamentPlayerEntity(
        id           = id,
        tournamentId = tournamentId,
        playerName   = name,
        playerColor  = "#FF0000",
        seed         = seed,
    )

    private fun buildFinishedMatch(
        id:             Long,
        tournamentId:   Long,
        playerAId:      Long,
        playerBId:      Long,
        winnerId:       Long,
        finalLifeTotals: String = "{${playerAId}:10,${playerBId}:0}",
    ) = TournamentMatchEntity(
        id             = id,
        tournamentId   = tournamentId,
        round          = 1,
        playerIds      = "[$playerAId,$playerBId]",
        winnerId       = winnerId,
        status         = "FINISHED",
        finalLifeTotals = finalLifeTotals,
    )

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        repository = TournamentRepositoryImpl(dao, progressionEventBus, generateNextRound, UnconfinedTestDispatcher())
        // Default match for finishMatch tests — winner (1L) is a valid participant
        coEvery { dao.getMatchById(any()) } returns TournamentMatchEntity(
            id = 100L, tournamentId = 1L, round = 1, playerIds = "[1,2]",
        )
        // finishMatch reads the parent tournament + players before the atomic advance (audit C1/C2).
        coEvery { dao.getTournamentById(1L) } returns TournamentEntity(
            id = 1L, name = "T", format = "STANDARD", structure = "ROUND_ROBIN", status = "ACTIVE",
        )
        coEvery { dao.getPlayers(1L) } returns listOf(buildPlayer(id = 1L), buildPlayer(id = 2L))
        // Default atomic finish-and-advance: round not complete (no advancement, no XP). Individual
        // tests override the returned AdvanceKind to drive the round-generated / finished / no-op paths.
        coEvery {
            dao.finishMatchAndAdvanceAtomically(any(), any(), any(), any(), any(), any(), any())
        } returns TournamentDao.AdvanceKind.ROUND_NOT_COMPLETE
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — REGRESSION A: insertTournamentAtomically is the ONLY DAO call
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid tournament when createTournament then insertTournamentAtomically is called exactly once`() = runTest {
        // Arrange
        val players = listOf("Alice" to "#FF0000", "Bob" to "#0000FF")
        coEvery { dao.insertTournamentAtomically(any(), any(), any()) } returns 1L

        // Act
        repository.createTournament(
            name              = "Friday Night",
            format            = "STANDARD",
            structure         = "ROUND_ROBIN",
            players           = players,
            matchesPerPairing = 1,
            isRandomPairings  = false,
        )

        // Assert: ONLY the atomic method — none of the old separate methods
        coVerify(exactly = 1) { dao.insertTournamentAtomically(any(), any(), any()) }
        coVerify(exactly = 0) { dao.insertTournament(any()) }
        coVerify(exactly = 0) { dao.insertPlayers(any()) }
        coVerify(exactly = 0) { dao.insertMatches(any()) }
    }

    @Test
    fun `given tournament creation when createTournament then player entities are passed with tournamentId=0`() = runTest {
        // Arrange — tournamentId is filled atomically inside insertTournamentAtomically
        val players = listOf("Alice" to "#FF0000", "Bob" to "#0000FF", "Charlie" to "#00FF00")

        val capturedPlayers = slot<List<TournamentPlayerEntity>>()
        coEvery { dao.insertTournamentAtomically(any(), capture(capturedPlayers), any()) } returns 1L

        // Act
        repository.createTournament(
            name              = "Test",
            format            = "COMMANDER",
            structure         = "ROUND_ROBIN",
            players           = players,
            matchesPerPairing = 1,
            isRandomPairings  = false,
        )

        // Assert: all player entities arrive at the DAO with tournamentId=0
        assertTrue(
            "All TournamentPlayerEntity must have tournamentId=0 before atomic insertion",
            capturedPlayers.captured.all { it.tournamentId == 0L }
        )
    }

    @Test
    fun `given 4 players when createTournament then 4 player entities are passed`() = runTest {
        // Arrange
        val players = listOf(
            "Alice" to "#FF0000", "Bob" to "#0000FF",
            "Charlie" to "#00FF00", "Dave" to "#FFFF00",
        )
        val capturedPlayers = slot<List<TournamentPlayerEntity>>()
        coEvery { dao.insertTournamentAtomically(any(), capture(capturedPlayers), any()) } returns 1L

        // Act
        repository.createTournament("T", "STANDARD", "ROUND_ROBIN", players, 1, false)

        // Assert
        assertEquals(4, capturedPlayers.captured.size)
    }

    @Test
    fun `given tournament when createTournament then tournament entity has status SETUP`() = runTest {
        // Arrange
        val capturedTournament = slot<TournamentEntity>()
        coEvery { dao.insertTournamentAtomically(capture(capturedTournament), any(), any()) } returns 1L

        // Act
        repository.createTournament("League", "COMMANDER", "SWISS", listOf("A" to "#FFF"), 1, true)

        // Assert
        assertEquals("SETUP", capturedTournament.captured.status)
    }

    @Test
    fun `given tournament when createTournament then returned id matches dao result`() = runTest {
        // Arrange
        coEvery { dao.insertTournamentAtomically(any(), any(), any()) } returns 42L

        // Act
        val id = repository.createTournament("T", "STANDARD", "ROUND_ROBIN", listOf("A" to "#FFF"), 1, false)

        // Assert
        assertEquals(42L, id)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — generateRoundRobin match count
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given 4 players and ROUND_ROBIN when createTournament then buildMatches generates correct match count`() = runTest {
        // Arrange — 4 players: 4*3/2 = 6 unique pairings
        val playerIds = listOf(10L, 20L, 30L, 40L)
        var capturedMatches: List<TournamentMatchEntity> = emptyList()

        coEvery {
            dao.insertTournamentAtomically(any(), any(), any())
        } answers {
            val buildMatchesFn = thirdArg<(Long, List<Long>) -> List<TournamentMatchEntity>>()
            capturedMatches = buildMatchesFn(1L, playerIds)
            1L
        }

        // Act
        repository.createTournament("T", "STANDARD", "ROUND_ROBIN",
            (1..4).map { "P$it" to "#FFF" }, 1, false)

        // Assert
        assertEquals(6, capturedMatches.size)
    }

    @Test
    fun `given 3 players and ROUND_ROBIN when createTournament then generates 3 matches`() = runTest {
        // Arrange — 3 players: 3*2/2 = 3 pairings
        val playerIds = listOf(10L, 20L, 30L)
        var capturedMatches: List<TournamentMatchEntity> = emptyList()

        coEvery {
            dao.insertTournamentAtomically(any(), any(), any())
        } answers {
            val buildMatchesFn = thirdArg<(Long, List<Long>) -> List<TournamentMatchEntity>>()
            capturedMatches = buildMatchesFn(1L, playerIds)
            1L
        }

        // Act
        repository.createTournament("T", "STANDARD", "ROUND_ROBIN",
            (1..3).map { "P$it" to "#FFF" }, 1, false)

        // Assert
        assertEquals(3, capturedMatches.size)
    }

    @Test
    fun `given matchesPerPairing=2 with 3 players when createTournament ROUND_ROBIN then generates 6 matches`() = runTest {
        // Arrange — 3 pairings x 2 matches each = 6
        val playerIds = listOf(10L, 20L, 30L)
        var capturedMatches: List<TournamentMatchEntity> = emptyList()

        coEvery {
            dao.insertTournamentAtomically(any(), any(), any())
        } answers {
            val buildMatchesFn = thirdArg<(Long, List<Long>) -> List<TournamentMatchEntity>>()
            capturedMatches = buildMatchesFn(1L, playerIds)
            1L
        }

        // Act
        repository.createTournament("T", "STANDARD", "ROUND_ROBIN",
            (1..3).map { "P$it" to "#FFF" }, 2, false)

        // Assert
        assertEquals(6, capturedMatches.size)
    }

    @Test
    fun `given 2 players and ROUND_ROBIN when createTournament then generates exactly 1 match`() = runTest {
        // Arrange — boundary: minimum viable round-robin
        val playerIds = listOf(10L, 20L)
        var capturedMatches: List<TournamentMatchEntity> = emptyList()

        coEvery {
            dao.insertTournamentAtomically(any(), any(), any())
        } answers {
            val buildMatchesFn = thirdArg<(Long, List<Long>) -> List<TournamentMatchEntity>>()
            capturedMatches = buildMatchesFn(1L, playerIds)
            1L
        }

        // Act
        repository.createTournament("T", "STANDARD", "ROUND_ROBIN",
            listOf("A" to "#FFF", "B" to "#000"), 1, false)

        // Assert
        assertEquals(1, capturedMatches.size)
    }

    @Test
    fun `given ROUND_ROBIN matches when createTournament then scheduledOrder is sequential from 0`() = runTest {
        // Arrange
        val playerIds = listOf(10L, 20L, 30L)
        var capturedMatches: List<TournamentMatchEntity> = emptyList()

        coEvery {
            dao.insertTournamentAtomically(any(), any(), any())
        } answers {
            val buildMatchesFn = thirdArg<(Long, List<Long>) -> List<TournamentMatchEntity>>()
            capturedMatches = buildMatchesFn(1L, playerIds)
            1L
        }

        // Act
        repository.createTournament("T", "STANDARD", "ROUND_ROBIN",
            (1..3).map { "P$it" to "#FFF" }, 1, false)

        // Assert
        val orders = capturedMatches.map { it.scheduledOrder }
        assertEquals(listOf(0, 1, 2), orders)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — REGRESSION B: double shuffle — SWISS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given even players and SWISS isRandom=false when createTournament then pairs respect original order`() = runTest {
        // Arrange — with isRandom=false, generateMatches does NOT shuffle.
        // generateFirstSwissRound must also NOT shuffle (regression: it used to).
        // Expected: [10,20] paired, [30,40] paired.
        val playerIds = listOf(10L, 20L, 30L, 40L)
        var capturedMatches: List<TournamentMatchEntity> = emptyList()

        coEvery {
            dao.insertTournamentAtomically(any(), any(), any())
        } answers {
            val buildMatchesFn = thirdArg<(Long, List<Long>) -> List<TournamentMatchEntity>>()
            capturedMatches = buildMatchesFn(1L, playerIds)
            1L
        }

        // Act
        repository.createTournament("T", "STANDARD", "SWISS",
            (1..4).map { "P$it" to "#FFF" }, 1, false)

        // Assert: first match pairs first two, second match pairs next two
        assertEquals("[10,20]", capturedMatches[0].playerIds)
        assertEquals("[30,40]", capturedMatches[1].playerIds)
    }

    @Test
    fun `given 5 players and SWISS when createTournament then generates 2 real matches plus 1 bye match`() = runTest {
        // Arrange — 5 players: pairs (0,1), (2,3), player 50 gets a bye (auto-finished match)
        val playerIds = listOf(10L, 20L, 30L, 40L, 50L)
        var capturedMatches: List<TournamentMatchEntity> = emptyList()

        coEvery {
            dao.insertTournamentAtomically(any(), any(), any())
        } answers {
            val buildMatchesFn = thirdArg<(Long, List<Long>) -> List<TournamentMatchEntity>>()
            capturedMatches = buildMatchesFn(1L, playerIds)
            1L
        }

        // Act
        repository.createTournament("T", "STANDARD", "SWISS",
            (1..5).map { "P$it" to "#FFF" }, 1, false)

        // Assert: 5 players → 2 real matches + 1 bye match = 3 total
        assertEquals(3, capturedMatches.size)
        val byeMatch = capturedMatches.find { it.playerIds == "[50]" }
        assertNotNull("Bye match for player 50 must exist", byeMatch)
        assertEquals("Bye match must be pre-finished", "FINISHED", byeMatch!!.status)
        assertEquals("Bye match winner must be the bye recipient", 50L, byeMatch.winnerId)
    }

    @Test
    fun `given 3 players and SWISS when createTournament then generates 1 real match and 1 bye match`() = runTest {
        // Arrange
        val playerIds = listOf(10L, 20L, 30L)
        var capturedMatches: List<TournamentMatchEntity> = emptyList()

        coEvery {
            dao.insertTournamentAtomically(any(), any(), any())
        } answers {
            val buildMatchesFn = thirdArg<(Long, List<Long>) -> List<TournamentMatchEntity>>()
            capturedMatches = buildMatchesFn(1L, playerIds)
            1L
        }

        // Act
        repository.createTournament("T", "STANDARD", "SWISS",
            (1..3).map { "P$it" to "#FFF" }, 1, false)

        // Assert: 1 real match + 1 bye
        assertEquals(2, capturedMatches.size)
        val byeMatch = capturedMatches.find { it.playerIds == "[30]" }
        assertNotNull("Bye match for player 30 must exist", byeMatch)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — REGRESSION B: double shuffle — SINGLE_ELIM
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given even players and SINGLE_ELIM isRandom=false when createTournament then pairs respect original order`() = runTest {
        // Arrange
        val playerIds = listOf(10L, 20L, 30L, 40L)
        var capturedMatches: List<TournamentMatchEntity> = emptyList()

        coEvery {
            dao.insertTournamentAtomically(any(), any(), any())
        } answers {
            val buildMatchesFn = thirdArg<(Long, List<Long>) -> List<TournamentMatchEntity>>()
            capturedMatches = buildMatchesFn(1L, playerIds)
            1L
        }

        // Act
        repository.createTournament("T", "STANDARD", "SINGLE_ELIM",
            (1..4).map { "P$it" to "#FFF" }, 1, false)

        // Assert: no shuffle — pairs must be [10,20] and [30,40]
        assertEquals(2, capturedMatches.size)
        assertEquals("[10,20]", capturedMatches[0].playerIds)
        assertEquals("[30,40]", capturedMatches[1].playerIds)
    }

    @Test
    fun `given 5 players and SINGLE_ELIM when createTournament then top seeds get byes and others play`() = runTest {
        // Arrange — 5 players → next power of 2 is 8 → 3 byes for top seeds; 2 real matches
        val playerIds = listOf(10L, 20L, 30L, 40L, 50L)
        var capturedMatches: List<TournamentMatchEntity> = emptyList()

        coEvery {
            dao.insertTournamentAtomically(any(), any(), any())
        } answers {
            val buildMatchesFn = thirdArg<(Long, List<Long>) -> List<TournamentMatchEntity>>()
            capturedMatches = buildMatchesFn(1L, playerIds)
            1L
        }

        // Act
        repository.createTournament("T", "STANDARD", "SINGLE_ELIM",
            (1..5).map { "P$it" to "#FFF" }, 1, false)

        // Assert: nextPow2(5)=8, byeCount=3 → 3 bye matches + 1 real match = 4 total
        // Top 3 seeds (10,20,30) get auto-finished byes; 40 vs 50 is the real match
        val byeMatches  = capturedMatches.filter { it.status == "FINISHED" && it.playerIds.trim('[', ']').split(",").size == 1 }
        val realMatches = capturedMatches.filter { it.status == "PENDING" }
        assertEquals("5 players → 3 byes + 1 real = 4 total", 4, capturedMatches.size)
        assertEquals("3 bye matches expected", 3, byeMatches.size)
        assertEquals("1 real match expected", 1, realMatches.size)
    }

    @Test
    fun `given 2 players and SINGLE_ELIM when createTournament then generates exactly 1 match`() = runTest {
        // Arrange — boundary
        val playerIds = listOf(10L, 20L)
        var capturedMatches: List<TournamentMatchEntity> = emptyList()

        coEvery {
            dao.insertTournamentAtomically(any(), any(), any())
        } answers {
            val buildMatchesFn = thirdArg<(Long, List<Long>) -> List<TournamentMatchEntity>>()
            capturedMatches = buildMatchesFn(1L, playerIds)
            1L
        }

        // Act
        repository.createTournament("T", "STANDARD", "SINGLE_ELIM",
            listOf("A" to "#FFF", "B" to "#000"), 1, false)

        // Assert
        assertEquals(1, capturedMatches.size)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — calculateStandings
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given 2 players with 1 finished match when calculateStandings then winner has 3 points and 1 win`() = runTest {
        // Arrange
        val p1 = buildPlayer(id = 1L, tournamentId = 1L, name = "Alice")
        val p2 = buildPlayer(id = 2L, tournamentId = 1L, name = "Bob")
        val match = buildFinishedMatch(
            id = 100L, tournamentId = 1L,
            playerAId = 1L, playerBId = 2L,
            winnerId = 1L,
            finalLifeTotals = "{1:15,2:0}",
        )
        coEvery { dao.getPlayers(1L) }       returns listOf(p1, p2)
        coEvery { dao.getFinishedMatches(1L) } returns listOf(match)

        // Act
        val standings = repository.calculateStandings(1L)

        // Assert: Alice wins → 3 points; Bob loses → 0 points
        val aliceStanding = standings.find { it.player.id == 1L }!!
        val bobStanding   = standings.find { it.player.id == 2L }!!

        assertEquals(1,  aliceStanding.wins)
        assertEquals(0,  aliceStanding.losses)
        assertEquals(3,  aliceStanding.points)
        assertEquals(0,  bobStanding.wins)
        assertEquals(1,  bobStanding.losses)
        assertEquals(0,  bobStanding.points)
    }

    @Test
    fun `given standings when calculateStandings then they are sorted by points descending`() = runTest {
        // Arrange — Alice has 2 wins, Bob has 1 win, Charlie has 0
        val alice   = buildPlayer(id = 1L, name = "Alice")
        val bob     = buildPlayer(id = 2L, name = "Bob")
        val charlie = buildPlayer(id = 3L, name = "Charlie")

        val matches = listOf(
            buildFinishedMatch(100L, 1L, 1L, 2L, winnerId = 1L, "{1:15,2:0}"),
            buildFinishedMatch(101L, 1L, 1L, 3L, winnerId = 1L, "{1:12,3:0}"),
            buildFinishedMatch(102L, 1L, 2L, 3L, winnerId = 2L, "{2:10,3:0}"),
        )

        coEvery { dao.getPlayers(1L) }        returns listOf(alice, bob, charlie)
        coEvery { dao.getFinishedMatches(1L) } returns matches

        // Act
        val standings = repository.calculateStandings(1L)

        // Assert: sorted by points → Alice(6), Bob(3), Charlie(0)
        assertEquals(1L, standings[0].player.id)  // Alice
        assertEquals(2L, standings[1].player.id)  // Bob
        assertEquals(3L, standings[2].player.id)  // Charlie
        assertEquals(1, standings[0].position)
        assertEquals(2, standings[1].position)
        assertEquals(3, standings[2].position)
    }

    @Test
    fun `given tie in points when calculateStandings then tiebreaker is OMW percent not life total`() = runTest {
        // Arrange: 2 players only — Alice and Bob each beat different opponents so their OMW% differs.
        // Using 4 players to set up different opponent strength without additional 3-pt ties.
        // Alice (1) beats Strong (3). Strong (3) beats Weak (4). Bob (2) beats Weak (4).
        // Points: Alice=3, Bob=3, Strong=3 (wins one), Weak=0.
        // But we only care about Alice vs Bob: Alice's OMW%(Strong=50%) > Bob's OMW%(Weak=33%).
        val alice   = buildPlayer(id = 1L, name = "Alice")
        val bob     = buildPlayer(id = 2L, name = "Bob")
        val strong  = buildPlayer(id = 3L, name = "Strong")
        val weak    = buildPlayer(id = 4L, name = "Weak")

        val matches = listOf(
            buildFinishedMatch(100L, 1L, 1L, 3L, winnerId = 1L, "{1:20,3:0}"),
            buildFinishedMatch(101L, 1L, 3L, 4L, winnerId = 3L, "{3:15,4:0}"),
            buildFinishedMatch(102L, 1L, 2L, 4L, winnerId = 2L, "{2:20,4:0}"),
        )

        coEvery { dao.getPlayers(1L) }        returns listOf(alice, bob, strong, weak)
        coEvery { dao.getFinishedMatches(1L) } returns matches

        // Act
        val standings = repository.calculateStandings(1L)

        // Assert: Alice has OMW%=50%, Bob has OMW%=33% → Alice is ranked above Bob
        // (life total is NOT used as tiebreaker — both have equal life totals here)
        val alicePos = standings.indexOfFirst { it.player.id == 1L }
        val bobPos   = standings.indexOfFirst { it.player.id == 2L }
        assertTrue("Alice must be ranked above Bob due to higher OMW%", alicePos < bobPos)
    }

    @Test
    fun `given finished match with null winnerId when calculateStandings then it counts as draw`() = runTest {
        // Arrange — Alice and Bob drew; both get 1 point
        val alice = buildPlayer(id = 1L, name = "Alice")
        val bob   = buildPlayer(id = 2L, name = "Bob")
        val drawMatch = TournamentMatchEntity(
            id             = 100L,
            tournamentId   = 1L,
            round          = 1,
            playerIds      = "[1,2]",
            winnerId       = null,    // null = draw
            status         = "FINISHED",
            finalLifeTotals = "{1:10,2:10}",
        )

        coEvery { dao.getPlayers(1L) }        returns listOf(alice, bob)
        coEvery { dao.getFinishedMatches(1L) } returns listOf(drawMatch)

        // Act
        val standings = repository.calculateStandings(1L)

        // Assert: both players have 1 draw = 1 point; no wins or losses
        val aliceStanding = standings.find { it.player.id == 1L }!!
        val bobStanding   = standings.find { it.player.id == 2L }!!
        assertEquals(0, aliceStanding.wins)
        assertEquals(0, aliceStanding.losses)
        assertEquals(1, aliceStanding.draws)
        assertEquals(1, aliceStanding.points)
        assertEquals(0, bobStanding.wins)
        assertEquals(0, bobStanding.losses)
        assertEquals(1, bobStanding.draws)
        assertEquals(1, bobStanding.points)
    }

    @Test
    fun `given no finished matches when calculateStandings then all players have 0 points`() = runTest {
        // Arrange
        val players = (1L..3L).map { buildPlayer(id = it, name = "P$it") }
        coEvery { dao.getPlayers(1L) }        returns players
        coEvery { dao.getFinishedMatches(1L) } returns emptyList()

        // Act
        val standings = repository.calculateStandings(1L)

        // Assert
        assertTrue(standings.all { it.points == 0 })
        assertTrue(standings.all { it.wins == 0 && it.losses == 0 })
    }

    @Test
    fun `given empty player list when calculateStandings then returns empty list`() = runTest {
        // Arrange — edge: no players in tournament
        coEvery { dao.getPlayers(1L) }        returns emptyList()
        coEvery { dao.getFinishedMatches(1L) } returns emptyList()

        // Act
        val standings = repository.calculateStandings(1L)

        // Assert
        assertTrue(standings.isEmpty())
    }

    @Test
    fun `given standings when calculateStandings then position starts at 1 not 0`() = runTest {
        // Arrange
        val players = listOf(buildPlayer(id = 1L), buildPlayer(id = 2L))
        coEvery { dao.getPlayers(1L) }        returns players
        coEvery { dao.getFinishedMatches(1L) } returns emptyList()

        // Act
        val standings = repository.calculateStandings(1L)

        // Assert
        assertEquals(1, standings[0].position)
        assertEquals(2, standings[1].position)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — parsePlayerIds / parseLifeTotals (internal via calculateStandings)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given match with empty finalLifeTotals when calculateStandings then lifeTotal is 0`() = runTest {
        // Arrange — edge: empty string for finalLifeTotals (before match was finished)
        val p1 = buildPlayer(id = 1L)
        val p2 = buildPlayer(id = 2L)
        val match = TournamentMatchEntity(
            id             = 1L,
            tournamentId   = 1L,
            round          = 1,
            playerIds      = "[1,2]",
            winnerId       = 1L,
            status         = "FINISHED",
            finalLifeTotals = "",   // empty — edge case
        )
        coEvery { dao.getPlayers(1L) }        returns listOf(p1, p2)
        coEvery { dao.getFinishedMatches(1L) } returns listOf(match)

        // Act — must not crash
        val standings = repository.calculateStandings(1L)

        // Assert: no exception; life totals default to 0
        assertTrue(standings.all { it.lifeTotal == 0 })
    }

    @Test
    fun `given match with malformed playerIds when calculateStandings then player is not counted`() = runTest {
        // Arrange — edge: malformed JSON-like string in playerIds
        val p1 = buildPlayer(id = 1L)
        val match = TournamentMatchEntity(
            id             = 1L,
            tournamentId   = 1L,
            round          = 1,
            playerIds      = "[abc,def]",  // non-numeric — should be ignored
            winnerId       = 1L,
            status         = "FINISHED",
            finalLifeTotals = "",
        )
        coEvery { dao.getPlayers(1L) }        returns listOf(p1)
        coEvery { dao.getFinishedMatches(1L) } returns listOf(match)

        // Act — must not crash
        val standings = repository.calculateStandings(1L)

        // Assert: no wins counted since IDs couldn't be parsed
        assertEquals(0, standings[0].wins)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — finishMatch serializes lifeTotals into the atomic finish-and-advance
    // ══════════════════════════════════════════════════════════════════════════
    //
    // finishMatch now routes the life-totals JSON into the SINGLE atomic DAO method
    // finishMatchAndAdvanceAtomically (the `lifeTotals` param), which finishes the match, advances the
    // round, and finishes the tournament in one transaction (audit C1/C2/C3). These tests assert the
    // serialization passed to that method.

    @Test
    fun `given lifeTotals map when finishMatch then atomic advance receives correct JSON string`() = runTest {
        // Arrange — capture the 5th positional argument (lifeTotals JSON)
        val capturedLifeJson = slot<String>()
        coEvery {
            dao.finishMatchAndAdvanceAtomically(any(), any(), any(), any(), capture(capturedLifeJson), any(), any())
        } returns TournamentDao.AdvanceKind.ROUND_NOT_COMPLETE

        // Act
        repository.finishMatch(matchId = 100L, winnerId = 1L, sessionId = 42L, lifeTotals = mapOf(1L to 15, 2L to 0))

        // Assert: JSON contains both entries — order may vary
        val json = capturedLifeJson.captured
        assertTrue("JSON must contain 1:15", json.contains("1:15"))
        assertTrue("JSON must contain 2:0",  json.contains("2:0"))
        assertTrue("JSON must start with {", json.startsWith("{"))
        assertTrue("JSON must end with }",   json.endsWith("}"))
    }

    @Test
    fun `given empty lifeTotals when finishMatch then atomic advance receives empty JSON object`() = runTest {
        // Arrange
        val capturedLifeJson = slot<String>()
        coEvery {
            dao.finishMatchAndAdvanceAtomically(any(), any(), any(), any(), capture(capturedLifeJson), any(), any())
        } returns TournamentDao.AdvanceKind.ROUND_NOT_COMPLETE

        // Act
        repository.finishMatch(100L, 1L, null, emptyMap())

        // Assert
        assertEquals("{}", capturedLifeJson.captured)
    }

    @Test
    fun `given winner not a participant when finishMatch then it throws before any write`() = runTest {
        // Match [1,2]; winner 99 is not a participant → the validation guard rejects before advancing.
        var threw = false
        try {
            repository.finishMatch(100L, winnerId = 99L, sessionId = null, lifeTotals = emptyMap())
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("non-participant winner must be rejected", threw)
        coVerify(exactly = 0) {
            dao.finishMatchAndAdvanceAtomically(any(), any(), any(), any(), any(), any(), any())
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 9 — single write path: outcome mapping + XP emission (C1 / C2 / C3)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given atomic advance returns TOURNAMENT_FINISHED when finishMatch then completion XP is emitted once`() = runTest {
        // C2: the game-played / manual path must emit TournamentCompleted exactly once on completion.
        val captured = slot<ProgressionEvent>()
        coEvery {
            dao.finishMatchAndAdvanceAtomically(any(), any(), any(), any(), any(), any(), any())
        } returns TournamentDao.AdvanceKind.TOURNAMENT_FINISHED
        coEvery { progressionEventBus.emit(capture(captured)) } returns Unit

        val outcome = repository.finishMatch(100L, 1L, 42L, mapOf(1L to 10, 2L to 0))

        assertEquals(MatchResultOutcome.TournamentFinished, outcome)
        coVerify(exactly = 1) { progressionEventBus.emit(any()) }
        assertTrue("must emit TournamentCompleted", captured.captured is ProgressionEvent.TournamentCompleted)
        assertEquals(1L, (captured.captured as ProgressionEvent.TournamentCompleted).tournamentId)
    }

    @Test
    fun `given atomic advance returns ROUND_GENERATED when finishMatch then no XP is emitted`() = runTest {
        // C1: advancing a round (not finishing) must NOT emit completion XP.
        coEvery {
            dao.finishMatchAndAdvanceAtomically(any(), any(), any(), any(), any(), any(), any())
        } returns TournamentDao.AdvanceKind.ROUND_GENERATED

        val outcome = repository.finishMatch(100L, 1L, null, emptyMap())

        assertEquals(MatchResultOutcome.RoundGenerated, outcome)
        coVerify(exactly = 0) { progressionEventBus.emit(any()) }
    }

    @Test
    fun `given atomic advance returns NO_OP when finishMatch then outcome is NoOp and no XP is emitted`() = runTest {
        // C3: a repeated / concurrent finish of an already-FINISHED match is a no-op — no double-grant.
        coEvery {
            dao.finishMatchAndAdvanceAtomically(any(), any(), any(), any(), any(), any(), any())
        } returns TournamentDao.AdvanceKind.NO_OP

        val outcome = repository.finishMatch(100L, 1L, null, emptyMap())

        assertEquals(MatchResultOutcome.NoOp, outcome)
        coVerify(exactly = 0) { progressionEventBus.emit(any()) }
    }

    @Test
    fun `given tournament already FINISHED when finishTournament then it no-ops and emits no XP`() = runTest {
        // Regression guard: finishTournament must not double-emit when already finished.
        coEvery { dao.getTournamentById(1L) } returns TournamentEntity(
            id = 1L, name = "T", format = "STANDARD", structure = "ROUND_ROBIN", status = "FINISHED",
        )

        repository.finishTournament(1L)

        coVerify(exactly = 0) { dao.finishTournament(any(), any()) }
        coVerify(exactly = 0) { progressionEventBus.emit(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — lifecycle delegation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid id when startTournament then dao updateStatus is called with ACTIVE`() = runTest {
        // Arrange
        coEvery { dao.updateStatus(any(), any()) } returns Unit

        // Act
        repository.startTournament(5L)

        // Assert
        coVerify(exactly = 1) { dao.updateStatus(5L, "ACTIVE") }
    }

    @Test
    fun `given no pending matches when isFinished then returns true`() = runTest {
        // Arrange — isFinished now requires at least one finished match AND no pending matches,
        // to avoid treating a brand-new empty tournament as "finished".
        coEvery { dao.getPendingMatchCount(1L) } returns 0
        coEvery { dao.getFinishedMatches(1L) } returns listOf(
            buildFinishedMatch(id = 1L, tournamentId = 1L, playerAId = 1L, playerBId = 2L, winnerId = 1L)
        )

        // Act
        val finished = repository.isFinished(1L)

        // Assert
        assertTrue(finished)
    }

    @Test
    fun `given pending matches when isFinished then returns false`() = runTest {
        // Arrange
        coEvery { dao.getPendingMatchCount(1L) } returns 2

        // Act
        val finished = repository.isFinished(1L)

        // Assert
        assertTrue(!finished)
    }
}
