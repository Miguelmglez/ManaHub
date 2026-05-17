package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.dao.GameSessionDao
import com.mmg.manahub.core.data.local.entity.GameSessionEntity
import com.mmg.manahub.core.data.local.entity.PlayerSessionEntity
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.feature.game.domain.model.EliminationReason
import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.feature.game.domain.model.GameResult
import com.mmg.manahub.feature.game.domain.model.Player
import com.mmg.manahub.feature.game.domain.model.PlayerResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [GameSessionRepositoryImpl].
 *
 * REGRESSION GROUP: "saveGameSession @Transaction fix"
 * Before the fix, saveGameSession() called insertSession() and insertPlayerSessions()
 * as two separate DAO calls — no @Transaction wrapper. A crash between them would leave
 * an orphaned game_sessions row with no player data, corrupting stats JOIN queries.
 * Fix: new insertSessionWithPlayers() @Transaction method. Players pass sessionId=0 and
 * the DAO fills the real auto-generated id atomically.
 * These tests verify the single-call contract and the sessionId=0 input invariant.
 */
class GameSessionRepositoryImplTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val dao = mockk<GameSessionDao>(relaxed = true)
    private lateinit var repository: GameSessionRepositoryImpl

    // ── Fixture helpers ───────────────────────────────────────────────────────

    private val defaultTheme = PlayerTheme.ALL[0]

    private fun buildPlayer(
        id:       Int     = 0,
        name:     String  = "Player ${id + 1}",
        life:     Int     = 20,
        poison:   Int     = 0,
        defeated: Boolean = false,
        isAppUser:Boolean = false,
        commanderDamage: Map<Int, Int> = emptyMap(),
    ) = Player(
        id              = id,
        name            = name,
        life            = life,
        poison          = poison,
        defeated        = defeated,
        isAppUser       = isAppUser,
        commanderDamage = commanderDamage,
        theme           = defaultTheme,
    )

    private fun buildPlayerResult(
        player:            Player,
        finalLife:         Int    = player.life,
        finalPoison:       Int    = player.poison,
        eliminationReason: EliminationReason? = null,
        cmdDealt:          Int    = 0,
        cmdReceived:       Int    = 0,
    ) = PlayerResult(
        player                        = player,
        finalLife                     = finalLife,
        finalPoison                   = finalPoison,
        eliminationReason             = eliminationReason,
        totalCommanderDamageDealt     = cmdDealt,
        totalCommanderDamageReceived  = cmdReceived,
    )

    private fun buildGameResult(
        winner:        Player,
        allPlayers:    List<Player>,
        playerResults: List<PlayerResult>,
        mode:          GameMode = GameMode.STANDARD,
        totalTurns:    Int      = 5,
        durationMs:    Long     = 60_000L,
    ) = GameResult(
        winner           = winner,
        allPlayers       = allPlayers,
        gameMode         = mode,
        totalTurns       = totalTurns,
        durationMs       = durationMs,
        playerResults    = playerResults,
        appUserWon       = winner.isAppUser,
        appUserFinalLife = winner.life,
        appUserName      = winner.name,
    )

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        repository = GameSessionRepositoryImpl(dao, UnconfinedTestDispatcher())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — REGRESSION: insertSessionWithPlayers is the ONLY DAO call
    //  (no separate insertSession / insertPlayerSessions calls allowed)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given 2-player game when saveGameSession then insertSessionWithPlayers is called exactly once`() = runTest {
        // Arrange
        val p0 = buildPlayer(id = 0, name = "Alice", life = 20, isAppUser = true)
        val p1 = buildPlayer(id = 1, name = "Bob",   life = 0,  defeated = true)
        val result = buildGameResult(
            winner        = p0,
            allPlayers    = listOf(p0, p1),
            playerResults = listOf(
                buildPlayerResult(p0, finalLife = 20),
                buildPlayerResult(p1, finalLife = 0, eliminationReason = EliminationReason.LIFE),
            ),
        )
        coEvery { dao.insertSessionWithPlayers(any(), any()) } returns 42L

        // Act
        val sessionId = repository.saveGameSession(result)

        // Assert: ONLY the atomic method is called — the two separate methods must NOT be called
        coVerify(exactly = 1) { dao.insertSessionWithPlayers(any(), any()) }
        coVerify(exactly = 0) { dao.insertSession(any()) }
        coVerify(exactly = 0) { dao.insertPlayerSessions(any()) }
        assertEquals(42L, sessionId)
    }

    @Test
    fun `given 4-player game when saveGameSession then insertSessionWithPlayers is called with 4 player entities`() = runTest {
        // Arrange
        val players = (0..3).map { buildPlayer(id = it, name = "Player ${it + 1}") }
        val winner  = players[0]
        val results = players.map { buildPlayerResult(it) }
        val gameResult = buildGameResult(winner, players, results)

        val capturedPlayers = slot<List<PlayerSessionEntity>>()
        coEvery { dao.insertSessionWithPlayers(any(), capture(capturedPlayers)) } returns 10L

        // Act
        repository.saveGameSession(gameResult)

        // Assert
        assertEquals(4, capturedPlayers.captured.size)
    }

    @Test
    fun `given 6-player game when saveGameSession then insertSessionWithPlayers is called with 6 player entities`() = runTest {
        // Arrange
        val players = (0..5).map { buildPlayer(id = it, name = "Player ${it + 1}") }
        val winner  = players[0]
        val results = players.map { buildPlayerResult(it) }
        val gameResult = buildGameResult(winner, players, results)

        val capturedPlayers = slot<List<PlayerSessionEntity>>()
        coEvery { dao.insertSessionWithPlayers(any(), capture(capturedPlayers)) } returns 11L

        // Act
        repository.saveGameSession(gameResult)

        // Assert
        assertEquals(6, capturedPlayers.captured.size)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — REGRESSION: all PlayerSessionEntity must have sessionId = 0
    //  (the DAO @Transaction fills the real id atomically)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given any game when saveGameSession then all player entities are passed with sessionId=0`() = runTest {
        // Arrange
        val p0 = buildPlayer(id = 0, name = "Alice", isAppUser = true)
        val p1 = buildPlayer(id = 1, name = "Bob")
        val p2 = buildPlayer(id = 2, name = "Charlie")
        val result = buildGameResult(
            winner        = p0,
            allPlayers    = listOf(p0, p1, p2),
            playerResults = listOf(p0, p1, p2).map { buildPlayerResult(it) },
        )

        val capturedPlayers = slot<List<PlayerSessionEntity>>()
        coEvery { dao.insertSessionWithPlayers(any(), capture(capturedPlayers)) } returns 99L

        // Act
        repository.saveGameSession(result)

        // Assert: every entity MUST arrive at the DAO with sessionId == 0
        // (the @Transaction method is responsible for filling the real id)
        assertTrue(
            "All PlayerSessionEntity passed to DAO must have sessionId=0",
            capturedPlayers.captured.all { it.sessionId == 0L }
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — Session entity construction correctness
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given game result when saveGameSession then session entity has correct mode and playerCount`() = runTest {
        // Arrange
        val players = listOf(buildPlayer(0), buildPlayer(1))
        val winner  = players[0]
        val result  = buildGameResult(
            winner        = winner,
            allPlayers    = players,
            playerResults = players.map { buildPlayerResult(it) },
            mode          = GameMode.COMMANDER,
            totalTurns    = 10,
            durationMs    = 120_000L,
        )

        val capturedSession = slot<GameSessionEntity>()
        coEvery { dao.insertSessionWithPlayers(capture(capturedSession), any()) } returns 1L

        // Act
        repository.saveGameSession(result)

        // Assert
        val session = capturedSession.captured
        assertEquals("COMMANDER",  session.mode)
        assertEquals(2,             session.playerCount)
        assertEquals(10,            session.totalTurns)
        assertEquals(120_000L,      session.durationMs)
        assertEquals(winner.id,     session.winnerId)
        assertEquals(winner.name,   session.winnerName)
    }

    @Test
    fun `given game result when saveGameSession then winner player entity has isWinner=true`() = runTest {
        // Arrange
        val p0 = buildPlayer(id = 0, name = "Winner")
        val p1 = buildPlayer(id = 1, name = "Loser",  defeated = true)
        val result = buildGameResult(
            winner        = p0,
            allPlayers    = listOf(p0, p1),
            playerResults = listOf(
                buildPlayerResult(p0, finalLife = 15),
                buildPlayerResult(p1, finalLife = 0, eliminationReason = EliminationReason.LIFE),
            ),
        )

        val capturedPlayers = slot<List<PlayerSessionEntity>>()
        coEvery { dao.insertSessionWithPlayers(any(), capture(capturedPlayers)) } returns 1L

        // Act
        repository.saveGameSession(result)

        // Assert
        val winnerEntity = capturedPlayers.captured.find { it.playerId == p0.id }
        val loserEntity  = capturedPlayers.captured.find { it.playerId == p1.id }
        assertTrue("Winner entity must have isWinner=true",   winnerEntity?.isWinner == true)
        assertTrue("Loser entity must have isWinner=false",   loserEntity?.isWinner  == false)
    }

    @Test
    fun `given player with LIFE elimination when saveGameSession then entity has correct eliminationReason`() = runTest {
        // Arrange
        val p0 = buildPlayer(id = 0, name = "Survivor")
        val p1 = buildPlayer(id = 1, name = "DeadPlayer", life = 0)
        val result = buildGameResult(
            winner        = p0,
            allPlayers    = listOf(p0, p1),
            playerResults = listOf(
                buildPlayerResult(p0, finalLife = 12),
                buildPlayerResult(p1, finalLife = 0, eliminationReason = EliminationReason.LIFE),
            ),
        )

        val capturedPlayers = slot<List<PlayerSessionEntity>>()
        coEvery { dao.insertSessionWithPlayers(any(), capture(capturedPlayers)) } returns 1L

        // Act
        repository.saveGameSession(result)

        // Assert
        val loserEntity = capturedPlayers.captured.find { it.playerId == p1.id }
        assertEquals("LIFE", loserEntity?.eliminationReason)
    }

    @Test
    fun `given player eliminated by poison when saveGameSession then entity has eliminationReason POISON`() = runTest {
        // Arrange
        val p0 = buildPlayer(id = 0, name = "Survivor")
        val p1 = buildPlayer(id = 1, name = "Poisoned", poison = 10)
        val result = buildGameResult(
            winner        = p0,
            allPlayers    = listOf(p0, p1),
            playerResults = listOf(
                buildPlayerResult(p0),
                buildPlayerResult(p1, finalPoison = 10, eliminationReason = EliminationReason.POISON),
            ),
        )

        val capturedPlayers = slot<List<PlayerSessionEntity>>()
        coEvery { dao.insertSessionWithPlayers(any(), capture(capturedPlayers)) } returns 1L

        // Act
        repository.saveGameSession(result)

        // Assert
        val poisonedEntity = capturedPlayers.captured.find { it.playerId == p1.id }
        assertEquals("POISON", poisonedEntity?.eliminationReason)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — Edge cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given 0 player results when saveGameSession then insertSessionWithPlayers is called with empty list`() = runTest {
        // Arrange — edge: empty playerResults (unusual but must not crash)
        val p0 = buildPlayer(id = 0, name = "Solo")
        val result = buildGameResult(
            winner        = p0,
            allPlayers    = listOf(p0),
            playerResults = emptyList(),   // explicit edge case
        )

        val capturedPlayers = slot<List<PlayerSessionEntity>>()
        coEvery { dao.insertSessionWithPlayers(any(), capture(capturedPlayers)) } returns 1L

        // Act — must not throw
        repository.saveGameSession(result)

        // Assert: DAO still called once; player list is empty
        coVerify(exactly = 1) { dao.insertSessionWithPlayers(any(), any()) }
        assertTrue(capturedPlayers.captured.isEmpty())
    }

    @Test
    fun `given winner not in playerResults when saveGameSession then remaining entities are still persisted`() = runTest {
        // Arrange — edge: winner is in allPlayers but NOT in playerResults
        val winner = buildPlayer(id = 0, name = "Alice")
        val loser  = buildPlayer(id = 1, name = "Bob")
        val result = buildGameResult(
            winner        = winner,
            allPlayers    = listOf(winner, loser),
            playerResults = listOf(buildPlayerResult(loser, eliminationReason = EliminationReason.LIFE)),
        )

        val capturedPlayers = slot<List<PlayerSessionEntity>>()
        coEvery { dao.insertSessionWithPlayers(any(), capture(capturedPlayers)) } returns 1L

        // Act — must not crash even though winner is absent from playerResults
        repository.saveGameSession(result)

        // Assert: one entity (the loser) was persisted; no crash
        coVerify(exactly = 1) { dao.insertSessionWithPlayers(any(), any()) }
        assertEquals(1, capturedPlayers.captured.size)
        assertEquals(loser.id, capturedPlayers.captured[0].playerId)
    }

    @Test
    fun `given saveGameSession when dao returns id then repository returns same id`() = runTest {
        // Arrange
        val p0 = buildPlayer(id = 0, isAppUser = true)
        val p1 = buildPlayer(id = 1)
        val result = buildGameResult(p0, listOf(p0, p1), listOf(buildPlayerResult(p0), buildPlayerResult(p1)))
        coEvery { dao.insertSessionWithPlayers(any(), any()) } returns 777L

        // Act
        val returnedId = repository.saveGameSession(result)

        // Assert
        assertEquals(777L, returnedId)
    }

    @Test
    fun `given COMMANDER game when saveGameSession then session entity mode is COMMANDER`() = runTest {
        // Arrange
        val p0 = buildPlayer(id = 0, life = 40, isAppUser = true)
        val p1 = buildPlayer(id = 1, life = 0)
        val result = buildGameResult(
            winner        = p0,
            allPlayers    = listOf(p0, p1),
            playerResults = listOf(buildPlayerResult(p0), buildPlayerResult(p1)),
            mode          = GameMode.COMMANDER,
        )

        val capturedSession = slot<GameSessionEntity>()
        coEvery { dao.insertSessionWithPlayers(capture(capturedSession), any()) } returns 1L

        // Act
        repository.saveGameSession(result)

        // Assert
        assertEquals("COMMANDER", capturedSession.captured.mode)
    }

    @Test
    fun `given player with commander damage elimination when saveGameSession then entity has COMMANDER_DAMAGE reason`() = runTest {
        // Arrange
        val p0 = buildPlayer(id = 0, name = "Commander")
        val p1 = buildPlayer(id = 1, name = "Victim")
        val result = buildGameResult(
            winner        = p0,
            allPlayers    = listOf(p0, p1),
            playerResults = listOf(
                buildPlayerResult(p0, cmdDealt = 21),
                buildPlayerResult(p1, eliminationReason = EliminationReason.COMMANDER_DAMAGE, cmdReceived = 21),
            ),
            mode = GameMode.COMMANDER,
        )

        val capturedPlayers = slot<List<PlayerSessionEntity>>()
        coEvery { dao.insertSessionWithPlayers(any(), capture(capturedPlayers)) } returns 1L

        // Act
        repository.saveGameSession(result)

        // Assert
        val victimEntity = capturedPlayers.captured.find { it.playerId == p1.id }
        assertEquals("COMMANDER_DAMAGE", victimEntity?.eliminationReason)
        assertEquals(21, victimEntity?.commanderDamageReceived)
    }
}
