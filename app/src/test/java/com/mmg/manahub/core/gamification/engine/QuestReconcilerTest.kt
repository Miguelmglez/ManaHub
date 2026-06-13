package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.entity.QuestInstanceEntity
import com.mmg.manahub.core.gamification.domain.QuestStableIdProvider
import com.mmg.manahub.core.gamification.domain.usecase.ClaimQuestRewardUseCase
import com.mmg.manahub.core.gamification.domain.usecase.ClaimResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Unit tests for [QuestReconciler]: it generates the current daily + weekly periods when missing,
 * EXPIRES stale ACTIVE instances, AUTO-CLAIMS stale COMPLETED ones, leaves CLAIMED rows alone, and is
 * idempotent on repeat (no regeneration once the period exists).
 *
 * Clock pinned to 2026-06-12 (UTC) → daily key "2026-06-12", weekly key "2026-W24".
 */
class QuestReconcilerTest {

    private lateinit var dao: GamificationDao
    private lateinit var stableIdProvider: QuestStableIdProvider
    private lateinit var claimUseCase: ClaimQuestRewardUseCase
    private lateinit var reconciler: QuestReconciler

    private val fixedInstant: Instant = Instant.parse("2026-06-12T10:00:00Z")
    private val now: Long get() = fixedInstant.toEpochMilli()
    private val dailyKey = "2026-06-12"
    private val weeklyKey = "2026-W24"

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        stableIdProvider = mockk()
        claimUseCase = mockk()
        reconciler = QuestReconciler(
            dao, stableIdProvider, claimUseCase,
            Clock.fixed(fixedInstant, ZoneId.of("UTC")), ZoneId.of("UTC"),
        )
        coEvery { stableIdProvider.stableId() } returns "device-A"
        coEvery { dao.getStaleQuests(any()) } returns emptyList()
        coEvery { dao.countQuestsForPeriod(any()) } returns 0
        coEvery { dao.getQuestsForPeriod(any()) } returns emptyList()
        coEvery { dao.upsertQuest(any()) } just Runs
        coEvery { claimUseCase.invoke(any()) } returns ClaimResult.AlreadyClaimed
    }

    private fun staleInstance(id: String, status: String) = QuestInstanceEntity(
        id = id, templateId = id.substringBefore(':'), period = "DAILY", periodKey = "2026-06-11",
        target = 1, progress = 1, status = status, expiresAt = now - 1, xpReward = 50, tokenReward = 0,
    )

    // ── Generation ────────────────────────────────────────────────────────────────

    @Test
    fun `generates the current daily and weekly periods when none exist`() = runTest {
        val saved = mutableListOf<QuestInstanceEntity>()
        coEvery { dao.upsertQuest(capture(saved)) } just Runs

        reconciler.reconcile()

        // 3 daily + 3 weekly instances generated for the current period keys.
        val dailyGenerated = saved.filter { it.periodKey == dailyKey }
        val weeklyGenerated = saved.filter { it.periodKey == weeklyKey }
        assertEquals(3, dailyGenerated.size)
        assertEquals(3, weeklyGenerated.size)
        assertTrue(dailyGenerated.all { it.status == "ACTIVE" && it.progress == 0 })
    }

    @Test
    fun `does not regenerate a period that already has instances (idempotent)`() = runTest {
        // Daily already generated, weekly not.
        coEvery { dao.countQuestsForPeriod(dailyKey) } returns 3
        coEvery { dao.countQuestsForPeriod(weeklyKey) } returns 0
        val saved = mutableListOf<QuestInstanceEntity>()
        coEvery { dao.upsertQuest(capture(saved)) } just Runs

        reconciler.reconcile()

        // Only weekly is generated; no new daily rows.
        assertTrue(saved.none { it.periodKey == dailyKey })
        assertEquals(3, saved.count { it.periodKey == weeklyKey })
    }

    @Test
    fun `reconcile twice does not double-generate`() = runTest {
        // First call: nothing exists → generates. Second call: simulate that the period now exists.
        coEvery { dao.countQuestsForPeriod(any()) } returnsMany listOf(0, 0, 3, 3)
        val saved = mutableListOf<QuestInstanceEntity>()
        coEvery { dao.upsertQuest(capture(saved)) } just Runs

        reconciler.reconcile() // generates 6
        reconciler.reconcile() // sees count 3/3 → generates 0

        assertEquals(6, saved.size)
    }

    @Test
    fun `the previous period template ids are read for the no-repeat rule`() = runTest {
        val prevKey = "2026-06-11"
        coEvery { dao.getQuestsForPeriod(prevKey) } returns listOf(
            staleInstance("daily_play_game:$prevKey", "CLAIMED"),
        )

        reconciler.reconcile()

        coVerify { dao.getQuestsForPeriod(prevKey) }
    }

    // ── Stale settlement ──────────────────────────────────────────────────────────

    @Test
    fun `a stale ACTIVE instance is expired`() = runTest {
        coEvery { dao.getStaleQuests(now) } returns listOf(
            staleInstance("daily_play_game:2026-06-11", "ACTIVE"),
        )
        val saved = slot<QuestInstanceEntity>()
        coEvery { dao.upsertQuest(capture(saved)) } just Runs

        reconciler.reconcile()

        // The first upsert is the expiry of the stale ACTIVE instance.
        coVerify { dao.upsertQuest(match { it.id == "daily_play_game:2026-06-11" && it.status == "EXPIRED" }) }
    }

    @Test
    fun `a stale COMPLETED instance is auto-claimed`() = runTest {
        coEvery { dao.getStaleQuests(now) } returns listOf(
            staleInstance("daily_win_game:2026-06-11", "COMPLETED"),
        )
        coEvery { claimUseCase.invoke("daily_win_game:2026-06-11") } returns
            ClaimResult.Claimed(xpAwarded = 50, newLevel = 1, leveledUp = false)

        reconciler.reconcile()

        coVerify { claimUseCase.invoke("daily_win_game:2026-06-11") }
        // It is NOT also expired (auto-claim handles the COMPLETED case).
        coVerify(exactly = 0) {
            dao.upsertQuest(match { it.id == "daily_win_game:2026-06-11" && it.status == "EXPIRED" })
        }
    }

    @Test
    fun `mixed stale instances are each settled by status`() = runTest {
        coEvery { dao.getStaleQuests(now) } returns listOf(
            staleInstance("a:2026-06-11", "ACTIVE"),
            staleInstance("b:2026-06-11", "COMPLETED"),
        )
        coEvery { claimUseCase.invoke(any()) } returns
            ClaimResult.Claimed(xpAwarded = 50, newLevel = 1, leveledUp = false)

        reconciler.reconcile()

        coVerify { dao.upsertQuest(match { it.id == "a:2026-06-11" && it.status == "EXPIRED" }) }
        coVerify { claimUseCase.invoke("b:2026-06-11") }
    }
}
