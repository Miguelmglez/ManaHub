package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.domain.model.AchievementUnlock
import com.mmg.manahub.core.gamification.domain.model.ProgressionOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** Stage order, per-stage isolation and the duplicate short-circuit of [GamificationEngineImpl.process]. */
class GamificationEngineImplTest {

    private val xpGranter: XpGranter = mockk()
    private val streakTracker: StreakTracker = mockk(relaxed = true)
    private val achievementEvaluator: AchievementEvaluator = mockk(relaxed = true)
    private val questEvaluator: QuestEvaluator = mockk(relaxed = true)
    private val entitlementGranter: EntitlementGranter = mockk(relaxed = true)
    private val crashReporter: CrashReporter = mockk(relaxed = true)

    private val event = ProgressionEvent.AppOpenedToday(
        localDate = "2026-09-24",
        occurredAt = Instant.parse("2026-09-24T10:00:00Z"),
    )

    private val unlock = AchievementUnlock(id = "STREAK_3", title = "Dedicated", emoji = "📅", tier = 1, xpReward = 50)

    private fun engine(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) = GamificationEngineImpl(
        bus = ProgressionEventBus(),
        xpGranter = xpGranter,
        achievementEvaluator = achievementEvaluator,
        questEvaluator = questEvaluator,
        streakTracker = streakTracker,
        entitlementGranter = entitlementGranter,
        defaultDispatcher = StandardTestDispatcher(scheduler),
        crashReporter = crashReporter,
    )

    @Test
    fun `stages run in order XP, streak, achievements, quests, entitlements`() = runTest {
        coEvery { xpGranter.grant(event) } returns XpGrantResult.Applied(ProgressionOutcome(xpGranted = 5, breakdown = emptyList(), newLevel = null, leveledUp = false))

        engine(testScheduler).process(event)

        coVerifyOrder {
            xpGranter.grant(event)
            streakTracker.process(event)
            achievementEvaluator.process(event, includeCounters = true)
            questEvaluator.process(event)
            entitlementGranter.grant(any())
        }
    }

    @Test
    fun `a duplicate event skips counters and quests but still runs streak and derived achievements`() = runTest {
        coEvery { xpGranter.grant(event) } returns XpGrantResult.Duplicate

        val outcome = engine(testScheduler).process(event)

        coVerify(exactly = 1) { streakTracker.process(event) }
        coVerify(exactly = 1) { achievementEvaluator.process(event, includeCounters = false) }
        coVerify(exactly = 0) { questEvaluator.process(any()) }
        assertEquals(0, outcome.xpGranted)
    }

    @Test
    fun `an event without XP still advances counters and quests`() = runTest {
        coEvery { xpGranter.grant(event) } returns XpGrantResult.NoXp

        engine(testScheduler).process(event)

        coVerify(exactly = 1) { achievementEvaluator.process(event, includeCounters = true) }
        coVerify(exactly = 1) { questEvaluator.process(event) }
    }

    @Test
    fun `a failing stage is reported and the later stages still run`() = runTest {
        coEvery { xpGranter.grant(event) } returns XpGrantResult.Applied(ProgressionOutcome(xpGranted = 5, breakdown = emptyList(), newLevel = null, leveledUp = false))
        coEvery { streakTracker.process(event) } throws IllegalStateException("streak boom")
        coEvery { achievementEvaluator.process(event, any()) } returns listOf(unlock)
        coEvery { questEvaluator.process(event) } throws IllegalStateException("quest boom")

        val outcome = engine(testScheduler).process(event)

        assertEquals(5, outcome.xpGranted)
        assertEquals(listOf(unlock), outcome.achievementUnlocks)
        coVerify(exactly = 1) { entitlementGranter.grant(match { it.achievementUnlocks == listOf(unlock) }) }
        verify { crashReporter.log("gamification_streak_failed") }
        verify { crashReporter.log("gamification_quest_eval_failed") }
    }

    @Test
    fun `an XP stage failure is treated as no XP, not as a duplicate`() = runTest {
        coEvery { xpGranter.grant(event) } throws IllegalStateException("xp boom")

        engine(testScheduler).process(event)

        coVerify(exactly = 1) { achievementEvaluator.process(event, includeCounters = true) }
        coVerify(exactly = 1) { questEvaluator.process(event) }
        verify { crashReporter.log("gamification_xp_grant_failed") }
    }
}
