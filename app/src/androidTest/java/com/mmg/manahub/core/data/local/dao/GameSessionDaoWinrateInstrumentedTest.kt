package com.mmg.manahub.core.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.entity.GameSessionEntity
import com.mmg.manahub.core.data.local.entity.PlayerSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room tests for [GameSessionDao]'s Phase 3 (2026-07 stats expansion) queries:
 * [GameSessionDao.observeWinrateByMode] and [GameSessionDao.observeWinrateByPlayerCount].
 *
 * Both queries are resolved against the LOCAL seat (`is_local = 1`, ADR-001) -- never a
 * `winnerName`/`playerName` string match (see memory `feedback_survey_winloss_isLocal`). Bucketing
 * the raw player count into "2 / 3 / 4+" is a presentation concern (`StatsViewModel`, covered in
 * `StatsViewModelTest`) -- this DAO returns a faithful `GROUP BY` of the exact persisted count.
 *
 * Requires a connected device or emulator (`./gradlew connectedAndroidTest`) -- NOT executed in
 * this environment (no adb-visible device), written and reviewed to compile/match the DAO's exact
 * query semantics, mirroring the existing `PlaytestDaoInstrumentedTest` pattern.
 */
@RunWith(AndroidJUnit4::class)
class GameSessionDaoWinrateInstrumentedTest {

    private lateinit var db: MtgDatabase
    private lateinit var gameSessionDao: GameSessionDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MtgDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameSessionDao = db.gameSessionDao()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Inserts a two-seat session (one local, one opponent) with the given mode/player count/result. */
    private suspend fun insertSession(mode: String, playerCount: Int, localIsWinner: Boolean) {
        val session = GameSessionEntity(
            durationMs = 1000L,
            mode = mode,
            totalTurns = 5,
            playerCount = playerCount,
            winnerId = if (localIsWinner) 0 else 1,
            winnerName = if (localIsWinner) "Local" else "Opponent",
        )
        val players = listOf(
            PlayerSessionEntity(
                sessionId = 0, playerId = 0, playerName = "Local", finalLife = 20, finalPoison = 0,
                eliminationReason = null, isWinner = localIsWinner, isLocal = true,
            ),
            PlayerSessionEntity(
                sessionId = 0, playerId = 1, playerName = "Opponent", finalLife = 0, finalPoison = 0,
                eliminationReason = "LIFE", isWinner = !localIsWinner, isLocal = false,
            ),
        )
        gameSessionDao.insertSessionWithPlayers(session, players)
    }

    // ── observeWinrateByMode ─────────────────────────────────────────────────────

    @Test
    fun observeWinrateByMode_groupsGamesByMode_andCountsLocalSeatWins() = runTest {
        insertSession(mode = "COMMANDER", playerCount = 4, localIsWinner = true)
        insertSession(mode = "COMMANDER", playerCount = 4, localIsWinner = false)
        insertSession(mode = "STANDARD", playerCount = 2, localIsWinner = true)

        val rows = gameSessionDao.observeWinrateByMode().first()
        val byMode = rows.associate { it.mode to (it.totalGames to it.wins) }

        assertEquals(2 to 1, byMode["COMMANDER"])
        assertEquals(1 to 1, byMode["STANDARD"])
    }

    @Test
    fun observeWinrateByMode_countsWinsFromTheLocalSeatOnly_neverANonLocalWinner() = runTest {
        // ADR-001 regression: the opponent seat wins this game, so the LOCAL seat's win count
        // must stay 0 even though the session itself has a winner.
        insertSession(mode = "COMMANDER", playerCount = 2, localIsWinner = false)

        val rows = gameSessionDao.observeWinrateByMode().first()
        val commander = rows.first { it.mode == "COMMANDER" }

        assertEquals(1, commander.totalGames)
        assertEquals(0, commander.wins)
    }

    // ── observeWinrateByPlayerCount ──────────────────────────────────────────────

    @Test
    fun observeWinrateByPlayerCount_groupsGamesByTheExactPersistedPlayerCount() = runTest {
        insertSession(mode = "COMMANDER", playerCount = 4, localIsWinner = true)
        insertSession(mode = "COMMANDER", playerCount = 3, localIsWinner = false)
        insertSession(mode = "COMMANDER", playerCount = 3, localIsWinner = true)
        insertSession(mode = "STANDARD", playerCount = 2, localIsWinner = true)

        val rows = gameSessionDao.observeWinrateByPlayerCount().first()
        val byCount = rows.associate { it.playerCount to (it.totalGames to it.wins) }

        // Bucketing 3/4/5+ into "2 / 3 / 4+" is done by StatsViewModel, not this raw GROUP BY --
        // each exact player count stays its own row here.
        assertEquals(1 to 1, byCount[4])
        assertEquals(2 to 1, byCount[3])
        assertEquals(1 to 1, byCount[2])
    }
}
