package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.entity.EntitlementEntity
import com.mmg.manahub.core.data.local.entity.PlayerProgressionEntity
import com.mmg.manahub.core.gamification.domain.LevelCurve
import com.mmg.manahub.core.gamification.domain.model.AchievementUnlock
import com.mmg.manahub.core.gamification.domain.model.ProgressionOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
 * Unit tests for [EntitlementGranter]: per-event level-up grants, achievement-unlock grants, idempotent
 * re-runs, and the retroactive [EntitlementGranter.reconcileAll] catch-up. The DAO is mocked; the clock
 * is pinned.
 *
 * Concrete catalog fixtures used:
 * - `title_aggressor` → LevelAtLeast(2), `frame_bronze` → LevelAtLeast(5).
 * - `title_collector` → AchievementUnlocked("COLLECTOR_50").
 * - `badge_first_win` → AchievementUnlocked("FIRST_WIN").
 */
class EntitlementGranterTest {

    private lateinit var dao: GamificationDao
    private lateinit var granter: EntitlementGranter

    private val fixedInstant: Instant = Instant.parse("2026-06-13T10:00:00Z")
    private val now: Long get() = fixedInstant.toEpochMilli()

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        granter = EntitlementGranter(dao, Clock.fixed(fixedInstant, ZoneId.of("UTC")))

        // Default: own nothing, every insert succeeds (rowId 1 = newly inserted), no progression row.
        coEvery { dao.hasEntitlement(any()) } returns false
        coEvery { dao.insertEntitlementIfAbsent(any()) } returns 1L
        coEvery { dao.getProgression() } returns null
        coEvery { dao.getUnlockedAchievementIds() } returns emptyList()
    }

    /** Builds a level-up outcome at [level]. */
    private fun levelUpOutcome(level: Int) = ProgressionOutcome(
        xpGranted = 100,
        breakdown = emptyList(),
        newLevel = level,
        leveledUp = true,
    )

    /** Builds an achievement-unlock outcome (no level-up). */
    private fun achievementOutcome(id: String) = ProgressionOutcome(
        xpGranted = 0,
        breakdown = emptyList(),
        newLevel = null,
        leveledUp = false,
        achievementUnlocks = listOf(AchievementUnlock(id = id, titleRes = 0, emoji = "", tier = 1, xpReward = 0)),
    )

    // ── Per-event level-up grants ──────────────────────────────────────────────────

    @Test
    fun `level-up to 5 grants all level-gated items up to that level`() = runTest {
        val rows = mutableListOf<EntitlementEntity>()
        coEvery { dao.insertEntitlementIfAbsent(capture(rows)) } returns 1L

        val granted = granter.grant(levelUpOutcome(level = 5))

        val grantedIds = granted.map { it.value }.toSet()
        // L2 title + several L2-5 mana badges + L5 bronze frame must all be in.
        assertTrue("title_aggressor (L2) should be granted", "title_aggressor" in grantedIds)
        assertTrue("frame_bronze (L5) should be granted", "frame_bronze" in grantedIds)
        // An item gated above 5 must NOT be granted.
        assertFalse("frame_silver (L10) must not be granted", "frame_silver" in grantedIds)
        // The persisted rows carry the LEVEL_UP source and the pinned clock.
        rows.forEach {
            assertEquals("LEVEL_UP", it.source)
            assertEquals(now, it.unlockedAt)
        }
    }

    @Test
    fun `an outcome with no level-up and no unlocks grants nothing`() = runTest {
        val granted = granter.grant(ProgressionOutcome.none)
        assertTrue(granted.isEmpty())
        coVerify(exactly = 0) { dao.insertEntitlementIfAbsent(any()) }
    }

    // ── Per-event achievement grants ────────────────────────────────────────────────

    @Test
    fun `unlocking COLLECTOR_50 grants the Collector title`() = runTest {
        // No level-up, so level resolves from the (empty) progression → level 1. Only the achievement
        // rule can fire.
        val rows = mutableListOf<EntitlementEntity>()
        coEvery { dao.insertEntitlementIfAbsent(capture(rows)) } returns 1L

        val granted = granter.grant(achievementOutcome("COLLECTOR_50"))

        val grantedIds = granted.map { it.value }.toSet()
        assertTrue("title_collector should be granted", "title_collector" in grantedIds)
        assertEquals("ACHIEVEMENT", rows.first { it.unlockableId == "title_collector" }.source)
    }

    @Test
    fun `unlocking an unrelated achievement grants nothing at level 1`() = runTest {
        val granted = granter.grant(achievementOutcome("MARATHON"))
        // MARATHON unlocks no cosmetic; at level 1 no level rule (min is L2) fires either.
        assertTrue(granted.isEmpty())
    }

    // ── Idempotency ─────────────────────────────────────────────────────────────────

    @Test
    fun `already-owned items are short-circuited and not re-inserted`() = runTest {
        // The player already owns everything → hasEntitlement true for all.
        coEvery { dao.hasEntitlement(any()) } returns true

        val granted = granter.grant(levelUpOutcome(level = 40))

        assertTrue("Nothing new should be granted", granted.isEmpty())
        coVerify(exactly = 0) { dao.insertEntitlementIfAbsent(any()) }
    }

    @Test
    fun `a concurrent insert (rowId -1) is not reported as a new grant`() = runTest {
        // hasEntitlement says "absent" but the insert loses the race (IGNORE → -1).
        coEvery { dao.hasEntitlement(any()) } returns false
        coEvery { dao.insertEntitlementIfAbsent(any()) } returns -1L

        val granted = granter.grant(levelUpOutcome(level = 5))

        assertTrue("rowId -1 must not count as newly granted", granted.isEmpty())
    }

    // ── Retroactive reconcile ─────────────────────────────────────────────────────────

    @Test
    fun `reconcileAll backfills level and achievement entitlements from current state`() = runTest {
        // Player is L20 (enough XP) and already unlocked COLLECTOR_50.
        val l20Xp = LevelCurve.cumulativeXpForLevel[20]
        coEvery { dao.getProgression() } returns PlayerProgressionEntity(
            id = PlayerProgressionEntity.SINGLETON_ID, totalXp = l20Xp, level = 20, updatedAt = now,
        )
        coEvery { dao.getUnlockedAchievementIds() } returns listOf("COLLECTOR_50")

        val granted = granter.grant(ProgressionOutcome.none) // grant() no-ops…
        assertTrue("grant() must not fire without level-up/unlock", granted.isEmpty())

        val reconciled = granter.reconcileAll().map { it.value }.toSet()
        // Level-gated up to 20 (frame_gold L20) + the achievement title.
        assertTrue("frame_gold (L20) should be reconciled", "frame_gold" in reconciled)
        assertTrue("title_aggressor (L2) should be reconciled", "title_aggressor" in reconciled)
        assertTrue("title_collector (COLLECTOR_50) should be reconciled", "title_collector" in reconciled)
        // L35 foil frame must NOT be reconciled at L20.
        assertFalse("frame_foil (L35) must not be reconciled at L20", "frame_foil" in reconciled)
    }

    @Test
    fun `reconcileAll is idempotent — owned items are not re-granted`() = runTest {
        coEvery { dao.getProgression() } returns PlayerProgressionEntity(
            id = PlayerProgressionEntity.SINGLETON_ID,
            totalXp = LevelCurve.cumulativeXpForLevel[10], level = 10, updatedAt = now,
        )
        coEvery { dao.hasEntitlement(any()) } returns true

        val reconciled = granter.reconcileAll()
        assertTrue("Second reconcile grants nothing", reconciled.isEmpty())
        coVerify(exactly = 0) { dao.insertEntitlementIfAbsent(any()) }
    }
}
