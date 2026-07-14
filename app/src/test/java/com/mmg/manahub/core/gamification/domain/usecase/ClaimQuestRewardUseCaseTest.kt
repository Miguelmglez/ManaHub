package com.mmg.manahub.core.gamification.domain.usecase

import com.mmg.manahub.core.gamification.domain.LevelCurve
import com.mmg.manahub.core.gamification.domain.model.ClaimResult
import com.mmg.manahub.core.gamification.domain.model.GrantResult
import com.mmg.manahub.core.gamification.domain.model.QuestClaimData
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.mmg.manahub.core.gamification.FixedClock
import kotlinx.datetime.Instant

/**
 * Unit tests for [ClaimQuestRewardUseCase]: a COMPLETED claim grants XP through the repository
 * primitives and flips to CLAIMED with the correct level-up; double-claim is idempotent (no second
 * grant); an ACTIVE quest is not claimable; an absent instance returns NotFound.
 *
 * After the KMP migration, [ClaimQuestRewardUseCase] lives in `commonMain` and depends on
 * [GamificationRepository], NOT [com.mmg.manahub.core.data.local.dao.GamificationDao]. Tests now
 * mock the repository boundary (not the DAO), which is the correct layering for a domain use-case test.
 */
class ClaimQuestRewardUseCaseTest {

    private lateinit var repo: GamificationRepository
    private lateinit var useCase: ClaimQuestRewardUseCase

    private val fixedInstant: Instant = Instant.parse("2026-06-12T10:00:00Z")
    private val now: Long get() = fixedInstant.toEpochMilliseconds()

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun questData(status: String, xpReward: Int = 50) =
        QuestClaimData(status = status, xpReward = xpReward)

    /** Simulates the repository computing the new level from 0 XP + [delta]. */
    private fun appliedResult(delta: Int): GrantResult {
        val newTotal = delta.toLong()
        return GrantResult(
            applied = true,
            previousLevel = LevelCurve.MIN_LEVEL,
            newLevel = LevelCurve.levelForTotalXp(newTotal),
        )
    }

    /** Applied result starting from [startXp] + [delta]. */
    private fun appliedResult(startXp: Long, delta: Int): GrantResult {
        val prevLevel = LevelCurve.levelForTotalXp(startXp)
        val newLevel  = LevelCurve.levelForTotalXp(startXp + delta)
        return GrantResult(applied = true, previousLevel = prevLevel, newLevel = newLevel)
    }

    private fun noopResult(): GrantResult =
        GrantResult(applied = false, previousLevel = LevelCurve.MIN_LEVEL, newLevel = LevelCurve.MIN_LEVEL)

    @Before
    fun setUp() {
        repo = mockk(relaxed = true)
        useCase = ClaimQuestRewardUseCase(repo, FixedClock(fixedInstant))
        // Default: markQuestClaimed is a no-op suspend fn.
        coEvery { repo.markQuestClaimed(any()) } just Runs
    }

    // ── Happy-path: claim a COMPLETED quest ───────────────────────────────────

    @Test
    fun `claiming a completed quest calls grantQuestClaimXp with the quest XP delta`() = runTest {
        coEvery { repo.getQuestForClaim("daily_play_game:2026-06-12") } returns
            questData(status = "COMPLETED", xpReward = 50)
        coEvery { repo.grantQuestClaimXp(any(), any(), any()) } returns appliedResult(delta = 50)

        val result = useCase("daily_play_game:2026-06-12")

        assertTrue(result is ClaimResult.Claimed)
        result as ClaimResult.Claimed
        assertEquals(50, result.xpAwarded)
        assertFalse("50 XP from 0 stays level 1", result.leveledUp)
        // Verify the use case passes the correct XP delta (not a precomputed total).
        coVerify(exactly = 1) { repo.grantQuestClaimXp("daily_play_game:2026-06-12", 50, any()) }
        coVerify(exactly = 1) { repo.markQuestClaimed("daily_play_game:2026-06-12") }
    }

    @Test
    fun `a weekly claim that crosses a level boundary reports leveledUp`() = runTest {
        // 200 XP from 0 → total 200 → level 2 (level 2 reached at 100 XP).
        val questId = "weekly_win_games:2026-W24"
        coEvery { repo.getQuestForClaim(questId) } returns questData(status = "COMPLETED", xpReward = 200)
        coEvery { repo.grantQuestClaimXp(any(), any(), any()) } returns appliedResult(delta = 200)

        val result = useCase(questId)

        assertTrue(result is ClaimResult.Claimed)
        result as ClaimResult.Claimed
        assertEquals(2, result.newLevel)
        assertTrue(result.leveledUp)
    }

    @Test
    fun `the delta passed to grantQuestClaimXp is the quest reward, not a precomputed total`() = runTest {
        // If the use case pre-reads progression and passes a total, the delta check here would
        // fail (50 != 30+50). Instead, the delta must exactly equal the reward.
        val questId = "daily_play_game:2026-06-12"
        coEvery { repo.getQuestForClaim(questId) } returns questData(status = "COMPLETED", xpReward = 50)
        coEvery { repo.grantQuestClaimXp(any(), any(), any()) } returns appliedResult(startXp = 30L, delta = 50)

        useCase(questId)

        // The use case must pass delta=50 (the reward), NOT delta=80 (30+50).
        coVerify(exactly = 1) { repo.grantQuestClaimXp(questId, 50, any()) }
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Test
    fun `a double-claim does not call grantQuestClaimXp and reports AlreadyClaimed`() = runTest {
        // Status already CLAIMED → short-circuits before the grant.
        coEvery { repo.getQuestForClaim(any()) } returns questData(status = "CLAIMED")

        val result = useCase("daily_play_game:2026-06-12")

        assertEquals(ClaimResult.AlreadyClaimed, result)
        coVerify(exactly = 0) { repo.grantQuestClaimXp(any(), any(), any()) }
    }

    @Test
    fun `a completed claim whose ledger row already exists is idempotent (no double XP)`() = runTest {
        // Status COMPLETED but grantQuestClaimXp returns applied=false (duplicate ledger key in the
        // repo impl) → AlreadyClaimed, markQuestClaimed still called to reconcile the status.
        coEvery { repo.getQuestForClaim(any()) } returns questData(status = "COMPLETED")
        coEvery { repo.grantQuestClaimXp(any(), any(), any()) } returns noopResult()

        val result = useCase("daily_play_game:2026-06-12")

        assertEquals(ClaimResult.AlreadyClaimed, result)
        coVerify(exactly = 1) { repo.markQuestClaimed("daily_play_game:2026-06-12") }
    }

    // ── Not-claimable statuses ────────────────────────────────────────────────

    @Test
    fun `an active (not yet completed) quest is not claimable`() = runTest {
        coEvery { repo.getQuestForClaim(any()) } returns questData(status = "ACTIVE")

        val result = useCase("daily_play_game:2026-06-12")

        assertEquals(ClaimResult.NotCompleted, result)
        coVerify(exactly = 0) { repo.grantQuestClaimXp(any(), any(), any()) }
    }

    @Test
    fun `an expired quest is not claimable`() = runTest {
        coEvery { repo.getQuestForClaim(any()) } returns questData(status = "EXPIRED")

        val result = useCase("daily_play_game:2026-06-12")

        assertEquals(ClaimResult.NotCompleted, result)
    }

    // ── Not-found ─────────────────────────────────────────────────────────────

    @Test
    fun `an absent instance returns NotFound`() = runTest {
        coEvery { repo.getQuestForClaim(any()) } returns null

        val result = useCase("missing:2026-06-12")

        assertEquals(ClaimResult.NotFound, result)
    }
}
