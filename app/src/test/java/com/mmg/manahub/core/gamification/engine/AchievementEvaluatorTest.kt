package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.dao.GamificationStatsDao
import com.mmg.manahub.core.data.local.entity.AchievementProgressEntity
import com.mmg.manahub.core.data.local.entity.XpTransactionEntity
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Unit tests for [AchievementEvaluator]: tier transitions, Family-A retroactive unlock, idempotent
 * tier XP, the unlocked_at persistence regression (the old NOW-on-recompute bug), and secret masking.
 *
 * Both DAOs are mocked. The DERIVED "GAMES_PLAYED" line (GAMES_PLAYED_10/50/100/500 single-tier defs)
 * and "COMMANDER_KILLER" (10) are used as concrete fixtures. The clock is pinned.
 */
class AchievementEvaluatorTest {

    private lateinit var dao: GamificationDao
    private lateinit var statsDao: GamificationStatsDao
    private lateinit var evaluator: AchievementEvaluator

    private val fixedInstant: Instant = Instant.parse("2026-06-12T10:00:00Z")
    private val now: Long get() = fixedInstant.toEpochMilli()

    private fun gameEvent(sessionId: Long = 1L, isLocalWin: Boolean = false) =
        ProgressionEvent.GameFinished(
            sessionId = sessionId, isLocalWin = isLocalWin, mode = "STANDARD", playerCount = 2,
            durationMs = 0L, winTurn = null, localFinalLife = null, occurredAt = fixedInstant,
        )

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        statsDao = mockk(relaxed = true)
        evaluator = AchievementEvaluator(dao, statsDao, Clock.fixed(fixedInstant, ZoneId.of("UTC")))

        // Default: no prior progress, no prior ledger txns, all stats 0.
        coEvery { dao.getAchievement(any()) } returns null
        coEvery { dao.hasTransaction(any()) } returns false
        coEvery { dao.getProgression() } returns null
        coEvery { dao.upsertAchievement(any()) } just Runs
        coEvery { dao.grantXpAtomically(any(), any(), any(), any()) } returns true
    }

    // ── Tier transitions (Family A) ───────────────────────────────────────────────

    @Test
    fun `crossing the games-played 10 threshold unlocks tier 1 and stamps unlocked_at`() = runTest {
        // Local seat has played 10 games.
        coEvery { statsDao.totalGames() } returns 10

        val rows = mutableListOf<AchievementProgressEntity>()
        coEvery { dao.upsertAchievement(capture(rows)) } just Runs

        val unlocks = evaluator.process(gameEvent())

        val played10 = rows.first { it.achievementId == "GAMES_PLAYED_10" }
        assertEquals(10, played10.currentValue)
        assertEquals(1, played10.tierReached)
        assertEquals(now, played10.unlockedAt)
        assertNull("celebrated_at must stay null so Chunk B can celebrate", played10.celebratedAt)
        assertTrue(unlocks.any { it.id == "GAMES_PLAYED_10" && it.tier == 1 })
    }

    @Test
    fun `progressing toward but below a threshold does not unlock`() = runTest {
        coEvery { statsDao.totalGames() } returns 9

        val rows = mutableListOf<AchievementProgressEntity>()
        coEvery { dao.upsertAchievement(capture(rows)) } just Runs

        val unlocks = evaluator.process(gameEvent())

        val played10 = rows.first { it.achievementId == "GAMES_PLAYED_10" }
        assertEquals(0, played10.tierReached)
        assertNull(played10.unlockedAt)
        assertTrue(unlocks.none { it.id == "GAMES_PLAYED_10" })
    }

    @Test
    fun `Family-A retroactive unlock fires when the aggregate already exceeds the threshold`() = runTest {
        // No prior achievement row, but the user already has 60 games → 10/50 unlock retroactively.
        coEvery { statsDao.totalGames() } returns 60
        coEvery { dao.getAchievement(any()) } returns null

        val unlocks = evaluator.process(gameEvent())

        assertTrue(unlocks.any { it.id == "GAMES_PLAYED_10" })
        assertTrue(unlocks.any { it.id == "GAMES_PLAYED_50" })
    }

    // ── unlocked_at persistence regression (the old NOW-on-recompute bug) ───────────

    @Test
    fun `re-evaluating an already-unlocked achievement does NOT change unlocked_at`() = runTest {
        val originalUnlock = 1_000L
        coEvery { statsDao.totalGames() } returns 12
        coEvery { dao.getAchievement("GAMES_PLAYED_10") } returns AchievementProgressEntity(
            achievementId = "GAMES_PLAYED_10",
            currentValue = 11,
            tierReached = 1,
            unlockedAt = originalUnlock,
            celebratedAt = null,
        )

        val rows = mutableListOf<AchievementProgressEntity>()
        coEvery { dao.upsertAchievement(capture(rows)) } just Runs

        evaluator.process(gameEvent())

        val played10 = rows.first { it.achievementId == "GAMES_PLAYED_10" }
        // The value advances, but unlocked_at is preserved (NOT re-stamped to `now`).
        assertEquals(12, played10.currentValue)
        assertEquals(originalUnlock, played10.unlockedAt)
    }

    // ── Idempotent tier XP ──────────────────────────────────────────────────────────

    @Test
    fun `crossing a tier grants that tier XP through the ledger key`() = runTest {
        coEvery { statsDao.totalGames() } returns 10
        val txn = slot<XpTransactionEntity>()
        coEvery { dao.grantXpAtomically(capture(txn), any(), any(), any()) } returns true

        evaluator.process(gameEvent())

        assertEquals("achievement:GAMES_PLAYED_10:tier:1", txn.captured.idempotencyKey)
    }

    @Test
    fun `a tier whose XP was already granted is not re-granted`() = runTest {
        coEvery { statsDao.totalGames() } returns 10
        // Ledger already has this tier's key → grant must be skipped.
        coEvery { dao.hasTransaction("achievement:GAMES_PLAYED_10:tier:1") } returns true

        evaluator.process(gameEvent())

        coVerify(exactly = 0) {
            dao.grantXpAtomically(
                match { it.idempotencyKey == "achievement:GAMES_PLAYED_10:tier:1" }, any(), any(), any(),
            )
        }
    }

    // ── Secret achievements ─────────────────────────────────────────────────────────

    @Test
    fun `secret one-life win unlocks when the local seat won at exactly 1 life`() = runTest {
        // GAMES_ENDED_AT_ONE_LIFE resolver returns 1.
        coEvery { statsDao.localWinsAtExactLife(1) } returns 1

        val rows = mutableListOf<AchievementProgressEntity>()
        coEvery { dao.upsertAchievement(capture(rows)) } just Runs

        val unlocks = evaluator.process(gameEvent(isLocalWin = true))

        val secret = rows.first { it.achievementId == "SECRET_ONE_LIFE_WIN" }
        assertEquals(1, secret.tierReached)
        assertEquals(now, secret.unlockedAt)
        assertTrue(unlocks.any { it.id == "SECRET_ONE_LIFE_WIN" })
    }

    @Test
    fun `secret stays locked while the underlying aggregate is zero`() = runTest {
        coEvery { statsDao.localWinsAtExactLife(1) } returns 0

        val rows = mutableListOf<AchievementProgressEntity>()
        coEvery { dao.upsertAchievement(capture(rows)) } just Runs

        evaluator.process(gameEvent(isLocalWin = true))

        val secret = rows.first { it.achievementId == "SECRET_ONE_LIFE_WIN" }
        assertEquals(0, secret.tierReached)
        assertNull(secret.unlockedAt)
    }

    // ── Family B counter (win streak) ────────────────────────────────────────────────

    @Test
    fun `win streak counter increments on a local win`() = runTest {
        coEvery { dao.getAchievement("WIN_STREAK_3") } returns AchievementProgressEntity(
            achievementId = "WIN_STREAK_3", currentValue = 2, tierReached = 0,
            unlockedAt = null, celebratedAt = null,
        )
        val rows = mutableListOf<AchievementProgressEntity>()
        coEvery { dao.upsertAchievement(capture(rows)) } just Runs

        val unlocks = evaluator.process(gameEvent(isLocalWin = true))

        val streak = rows.first { it.achievementId == "WIN_STREAK_3" }
        assertEquals(3, streak.currentValue)
        assertEquals(1, streak.tierReached)
        assertTrue(unlocks.any { it.id == "WIN_STREAK_3" })
    }

    @Test
    fun `win streak counter resets to zero on a loss`() = runTest {
        coEvery { dao.getAchievement("WIN_STREAK_3") } returns AchievementProgressEntity(
            achievementId = "WIN_STREAK_3", currentValue = 2, tierReached = 0,
            unlockedAt = null, celebratedAt = null,
        )
        val rows = mutableListOf<AchievementProgressEntity>()
        coEvery { dao.upsertAchievement(capture(rows)) } just Runs

        evaluator.process(gameEvent(isLocalWin = false))

        val streak = rows.first { it.achievementId == "WIN_STREAK_3" }
        assertEquals(0, streak.currentValue)
        assertEquals(0, streak.tierReached)
    }
}
