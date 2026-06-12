package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.entity.QuestInstanceEntity
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
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
 * Unit tests for [QuestEvaluator]: it advances only the matching active instances for the CURRENT
 * period, caps at target, flips ACTIVE→COMPLETED, ignores non-ACTIVE/absent instances, and applies
 * count-based vs conditional advance functions.
 *
 * The clock is pinned to 2026-06-12 (UTC) → daily key "2026-06-12", weekly key "2026-W24".
 */
class QuestEvaluatorTest {

    private lateinit var dao: GamificationDao
    private lateinit var evaluator: QuestEvaluator

    private val fixedInstant: Instant = Instant.parse("2026-06-12T10:00:00Z")
    private val dailyKey = "2026-06-12"
    private val weeklyKey = "2026-W24"

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        evaluator = QuestEvaluator(dao, Clock.fixed(fixedInstant, ZoneId.of("UTC")), ZoneId.of("UTC"))
        coEvery { dao.getQuest(any()) } returns null
        coEvery { dao.upsertQuest(any()) } just Runs
    }

    private fun gameEvent(isLocalWin: Boolean = false, playerCount: Int = 2) =
        ProgressionEvent.GameFinished(
            sessionId = 1L, isLocalWin = isLocalWin, mode = "STANDARD", playerCount = playerCount,
            durationMs = 0L, winTurn = null, localFinalLife = null, occurredAt = fixedInstant,
        )

    private fun dailyInstance(
        templateId: String,
        progress: Int,
        target: Int,
        status: String = "ACTIVE",
        key: String = dailyKey,
    ) = QuestInstanceEntity(
        id = "$templateId:$key",
        templateId = templateId,
        period = "DAILY",
        periodKey = key,
        target = target,
        progress = progress,
        status = status,
        expiresAt = Long.MAX_VALUE,
        xpReward = 50,
        tokenReward = 0,
    )

    // ── Advances only matching active instances ──────────────────────────────────

    @Test
    fun `play-game quest advances by one on a finished game`() = runTest {
        coEvery { dao.getQuest("daily_play_game:$dailyKey") } returns
            dailyInstance("daily_play_game", progress = 0, target = 1)

        val deltas = evaluator.process(gameEvent())

        val row = slot<QuestInstanceEntity>()
        coVerify { dao.upsertQuest(capture(row)) }
        assertEquals(1, row.captured.progress)
        assertEquals("COMPLETED", row.captured.status)
        assertTrue(deltas.any { it.instanceId == "daily_play_game:$dailyKey" && it.justCompleted })
    }

    @Test
    fun `win quest does not advance on a loss`() = runTest {
        coEvery { dao.getQuest("daily_win_game:$dailyKey") } returns
            dailyInstance("daily_win_game", progress = 0, target = 1)

        val deltas = evaluator.process(gameEvent(isLocalWin = false))

        coVerify(exactly = 0) { dao.upsertQuest(any()) }
        assertTrue(deltas.isEmpty())
    }

    @Test
    fun `win quest advances on a local win`() = runTest {
        coEvery { dao.getQuest("daily_win_game:$dailyKey") } returns
            dailyInstance("daily_win_game", progress = 0, target = 1)

        val deltas = evaluator.process(gameEvent(isLocalWin = true))

        assertTrue(deltas.any { it.templateId == "daily_win_game" && it.justCompleted })
    }

    // ── Caps at target ────────────────────────────────────────────────────────────

    @Test
    fun `progress is clamped at the target`() = runTest {
        // weekly_add_cards target 20, currently 19; a CardsAdded of 5 caps at 20 (not 24).
        coEvery { dao.getQuest("weekly_add_cards:$weeklyKey") } returns QuestInstanceEntity(
            id = "weekly_add_cards:$weeklyKey", templateId = "weekly_add_cards", period = "WEEKLY",
            periodKey = weeklyKey, target = 20, progress = 19, status = "ACTIVE",
            expiresAt = Long.MAX_VALUE, xpReward = 200, tokenReward = 0,
        )

        val event = ProgressionEvent.CardsAdded(addedCopies = 3, addedUnique = 2, occurredAt = fixedInstant)
        val deltas = evaluator.process(event)

        val row = slot<QuestInstanceEntity>()
        coVerify { dao.upsertQuest(capture(row)) }
        assertEquals(20, row.captured.progress)
        assertEquals("COMPLETED", row.captured.status)
        assertEquals(20, deltas.single().newProgress)
    }

    @Test
    fun `an already-capped active instance is not re-written`() = runTest {
        // progress already == target but somehow still ACTIVE → no net change, skip the upsert.
        coEvery { dao.getQuest("daily_play_game:$dailyKey") } returns
            dailyInstance("daily_play_game", progress = 1, target = 1)

        val deltas = evaluator.process(gameEvent())

        coVerify(exactly = 0) { dao.upsertQuest(any()) }
        assertTrue(deltas.isEmpty())
    }

    // ── Ignores non-ACTIVE / absent instances ────────────────────────────────────

    @Test
    fun `a completed instance is not advanced again`() = runTest {
        coEvery { dao.getQuest("daily_play_game:$dailyKey") } returns
            dailyInstance("daily_play_game", progress = 1, target = 1, status = "COMPLETED")

        val deltas = evaluator.process(gameEvent())

        coVerify(exactly = 0) { dao.upsertQuest(any()) }
        assertTrue(deltas.isEmpty())
    }

    @Test
    fun `an absent instance is skipped`() = runTest {
        // No quest rows exist (default getQuest returns null) → nothing advances, no crash.
        val deltas = evaluator.process(gameEvent())
        coVerify(exactly = 0) { dao.upsertQuest(any()) }
        assertTrue(deltas.isEmpty())
    }

    // ── Multi-increment + conditional advance ────────────────────────────────────

    @Test
    fun `CardsAdded advances by the total of unique plus copies`() = runTest {
        coEvery { dao.getQuest("daily_add_cards:$dailyKey") } returns
            dailyInstance("daily_add_cards", progress = 0, target = 3)

        val event = ProgressionEvent.CardsAdded(addedCopies = 1, addedUnique = 2, occurredAt = fixedInstant)
        val deltas = evaluator.process(event)

        assertEquals(3, deltas.single().newProgress) // 2 unique + 1 copy
        assertTrue(deltas.single().justCompleted)
    }

    @Test
    fun `scan quest advances by the batch count`() = runTest {
        coEvery { dao.getQuest("daily_scan_cards:$dailyKey") } returns
            dailyInstance("daily_scan_cards", progress = 0, target = 2)

        val event = ProgressionEvent.CardScanned(scanBatchId = "b1", count = 2, occurredAt = fixedInstant)
        val deltas = evaluator.process(event)

        assertEquals(2, deltas.single().newProgress)
        assertTrue(deltas.single().justCompleted)
    }

    @Test
    fun `multiplayer weekly quest only advances on a 4-plus player game`() = runTest {
        coEvery { dao.getQuest("weekly_multiplayer_game:$weeklyKey") } returns QuestInstanceEntity(
            id = "weekly_multiplayer_game:$weeklyKey", templateId = "weekly_multiplayer_game",
            period = "WEEKLY", periodKey = weeklyKey, target = 1, progress = 0, status = "ACTIVE",
            expiresAt = Long.MAX_VALUE, xpReward = 200, tokenReward = 0,
        )

        val twoPlayer = evaluator.process(gameEvent(playerCount = 2))
        assertTrue(twoPlayer.none { it.templateId == "weekly_multiplayer_game" })

        val fourPlayer = evaluator.process(gameEvent(playerCount = 4))
        assertTrue(fourPlayer.any { it.templateId == "weekly_multiplayer_game" && it.justCompleted })
    }

    @Test
    fun `explore quest only advances for the matching feature key`() = runTest {
        coEvery { dao.getQuest("daily_explore_deck_doctor:$dailyKey") } returns
            dailyInstance("daily_explore_deck_doctor", progress = 0, target = 1)

        val wrong = ProgressionEvent.FeatureExplored(featureKey = "something_else", occurredAt = fixedInstant)
        assertTrue(evaluator.process(wrong).isEmpty())

        val right = ProgressionEvent.FeatureExplored(featureKey = "deck_doctor", occurredAt = fixedInstant)
        val deltas = evaluator.process(right)
        assertTrue(deltas.any { it.templateId == "daily_explore_deck_doctor" && it.justCompleted })
    }

    @Test
    fun `advancing below the target keeps the quest ACTIVE`() = runTest {
        coEvery { dao.getQuest("weekly_play_games:$weeklyKey") } returns QuestInstanceEntity(
            id = "weekly_play_games:$weeklyKey", templateId = "weekly_play_games", period = "WEEKLY",
            periodKey = weeklyKey, target = 7, progress = 0, status = "ACTIVE",
            expiresAt = Long.MAX_VALUE, xpReward = 200, tokenReward = 0,
        )

        val deltas = evaluator.process(gameEvent())

        val row = slot<QuestInstanceEntity>()
        coVerify { dao.upsertQuest(capture(row)) }
        assertEquals(1, row.captured.progress)
        assertEquals("ACTIVE", row.captured.status)
        assertFalse(deltas.single().justCompleted)
    }
}
