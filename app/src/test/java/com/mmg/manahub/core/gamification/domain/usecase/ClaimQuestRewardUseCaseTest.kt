package com.mmg.manahub.core.gamification.domain.usecase

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.entity.PlayerProgressionEntity
import com.mmg.manahub.core.data.local.entity.QuestInstanceEntity
import com.mmg.manahub.core.data.local.entity.XpTransactionEntity
import com.mmg.manahub.core.gamification.domain.LevelCurve
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Unit tests for [ClaimQuestRewardUseCase]: a COMPLETED claim grants XP through the ledger key and
 * flips to CLAIMED with the correct level-up; double-claim is idempotent (no second grant); an ACTIVE
 * quest is not claimable; an absent instance returns NotFound.
 */
class ClaimQuestRewardUseCaseTest {

    private lateinit var dao: GamificationDao
    private lateinit var useCase: ClaimQuestRewardUseCase

    private val fixedInstant: Instant = Instant.parse("2026-06-12T10:00:00Z")
    private val now: Long get() = fixedInstant.toEpochMilli()

    private fun instance(status: String, xpReward: Int = 50, id: String = "daily_play_game:2026-06-12") =
        QuestInstanceEntity(
            id = id, templateId = "daily_play_game", period = "DAILY", periodKey = "2026-06-12",
            target = 1, progress = 1, status = status, expiresAt = Long.MAX_VALUE,
            xpReward = xpReward, tokenReward = 0,
        )

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        useCase = ClaimQuestRewardUseCase(dao, Clock.fixed(fixedInstant, ZoneId.of("UTC")))
        coEvery { dao.getProgression() } returns null // 0 XP baseline
        coEvery { dao.upsertQuest(any()) } just Runs
        // The use case reads result.newLevel/previousLevel, so the levels must be real: derive the
        // new total/level from the (mocked) current progression + the delta the DAO is passed.
        coEvery {
            dao.grantXpAtomically(any(), any(), any(), any())
        } coAnswers { appliedResult(secondArg<Int>()) }
    }

    /** Builds an applied [GamificationDao.GrantResult] from the current (mocked) progression + delta. */
    private suspend fun appliedResult(amount: Int): GamificationDao.GrantResult {
        val current = dao.getProgression()?.totalXp ?: 0L
        val newTotal = current + amount
        return GamificationDao.GrantResult(
            applied = true,
            previousTotalXp = current,
            newTotalXp = newTotal,
            previousLevel = LevelCurve.levelForTotalXp(current),
            newLevel = LevelCurve.levelForTotalXp(newTotal),
        )
    }

    /** A duplicate/no-op result that leaves progression unchanged. */
    private fun noopResult(): GamificationDao.GrantResult = GamificationDao.GrantResult(
        applied = false,
        previousTotalXp = 0L,
        newTotalXp = 0L,
        previousLevel = LevelCurve.MIN_LEVEL,
        newLevel = LevelCurve.MIN_LEVEL,
    )

    @Test
    fun `claiming a completed quest grants XP via the ledger key and flips to CLAIMED`() = runTest {
        coEvery { dao.getQuest(any()) } returns instance(status = "COMPLETED", xpReward = 50)
        val txn = slot<XpTransactionEntity>()
        coEvery {
            dao.grantXpAtomically(capture(txn), any(), any(), any())
        } coAnswers { appliedResult(secondArg<Int>()) }
        val saved = slot<QuestInstanceEntity>()
        coEvery { dao.upsertQuest(capture(saved)) } just Runs

        val result = useCase("daily_play_game:2026-06-12")

        assertTrue(result is ClaimResult.Claimed)
        result as ClaimResult.Claimed
        assertEquals(50, result.xpAwarded)
        assertFalse("50 XP from 0 stays level 1", result.leveledUp)
        assertEquals("quest_claim:daily_play_game:2026-06-12", txn.captured.idempotencyKey)
        assertEquals("CLAIMED", saved.captured.status)
    }

    @Test
    fun `a weekly claim that crosses a level boundary reports leveledUp`() = runTest {
        // 200 XP from 0 → total 200 → level 2 (level 2 reached at 100 XP). prevLevel = 1.
        coEvery { dao.getQuest(any()) } returns
            instance(status = "COMPLETED", xpReward = 200, id = "weekly_win_games:2026-W24")

        val result = useCase("weekly_win_games:2026-W24")

        assertTrue(result is ClaimResult.Claimed)
        result as ClaimResult.Claimed
        assertEquals(2, result.newLevel)
        assertTrue(result.leveledUp)
    }

    @Test
    fun `the reward delta passed to the grant is the quest XP and yields current plus reward`() = runTest {
        coEvery { dao.getProgression() } returns PlayerProgressionEntity(
            id = PlayerProgressionEntity.SINGLETON_ID, totalXp = 30L, level = 1, updatedAt = 0L,
        )
        coEvery { dao.getQuest(any()) } returns instance(status = "COMPLETED", xpReward = 50)
        // The use case passes the reward DELTA (not a precomputed total); the DAO derives the new total.
        val amount = slot<Int>()
        coEvery {
            dao.grantXpAtomically(any(), capture(amount), any(), any())
        } coAnswers { appliedResult(secondArg<Int>()) }

        useCase("daily_play_game:2026-06-12")

        // The delta is the reward, and the resulting total is current (30) + reward (50) = 80.
        assertEquals(50, amount.captured)
        assertEquals(80L, appliedResult(amount.captured).newTotalXp)
    }

    @Test
    fun `a double-claim does not grant XP twice and reports AlreadyClaimed`() = runTest {
        // Second claim: status already CLAIMED → short-circuits, no ledger write at all.
        coEvery { dao.getQuest(any()) } returns instance(status = "CLAIMED")

        val result = useCase("daily_play_game:2026-06-12")

        assertEquals(ClaimResult.AlreadyClaimed, result)
        coVerify(exactly = 0) { dao.grantXpAtomically(any(), any(), any(), any()) }
    }

    @Test
    fun `a completed claim whose ledger row already exists is idempotent (no double XP)`() = runTest {
        // status COMPLETED but the ledger insert returns false (duplicate key) → AlreadyClaimed, status
        // reconciled to CLAIMED, progression NOT advanced a second time.
        coEvery { dao.getQuest(any()) } returns instance(status = "COMPLETED")
        coEvery { dao.grantXpAtomically(any(), any(), any(), any()) } returns noopResult()
        val saved = slot<QuestInstanceEntity>()
        coEvery { dao.upsertQuest(capture(saved)) } just Runs

        val result = useCase("daily_play_game:2026-06-12")

        assertEquals(ClaimResult.AlreadyClaimed, result)
        assertEquals("CLAIMED", saved.captured.status)
    }

    @Test
    fun `an active (not yet completed) quest is not claimable`() = runTest {
        coEvery { dao.getQuest(any()) } returns instance(status = "ACTIVE")

        val result = useCase("daily_play_game:2026-06-12")

        assertEquals(ClaimResult.NotCompleted, result)
        coVerify(exactly = 0) { dao.grantXpAtomically(any(), any(), any(), any()) }
    }

    @Test
    fun `an expired quest is not claimable`() = runTest {
        coEvery { dao.getQuest(any()) } returns instance(status = "EXPIRED")

        val result = useCase("daily_play_game:2026-06-12")

        assertEquals(ClaimResult.NotCompleted, result)
    }

    @Test
    fun `an absent instance returns NotFound`() = runTest {
        coEvery { dao.getQuest(any()) } returns null

        val result = useCase("missing:2026-06-12")

        assertEquals(ClaimResult.NotFound, result)
    }
}
