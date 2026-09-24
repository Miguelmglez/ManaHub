package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.dao.GamificationStatsDao
import com.mmg.manahub.core.data.local.entity.AchievementProgressEntity
import com.mmg.manahub.core.gamification.domain.catalog.AchievementCatalog
import com.mmg.manahub.core.gamification.domain.catalog.Family
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import com.mmg.manahub.core.FeatureFlags
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.mmg.manahub.core.gamification.FixedClock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * Unit tests for the PURE backfill computation [AchievementBackfill.computeBackfillRows]: backfilled
 * unlocks set celebrated_at (no celebration queued), existing unlocks are preserved, and tier XP is
 * planned only for newly-crossed tiers.
 */
class AchievementBackfillTest {

    private val now = Instant.parse("2026-06-12T10:00:00Z").toEpochMilliseconds()

    // The pure function needs no DAO/clock interaction; mocks satisfy the constructor only.
    private val backfill = AchievementBackfill(
        dao = mockk<GamificationDao>(relaxed = true),
        statsDao = mockk<GamificationStatsDao>(relaxed = true),
        clock = FixedClock(Instant.fromEpochMilliseconds(now)),
        defaultDispatcher = Dispatchers.Unconfined,
    )

    private val derivedDefs = AchievementCatalog.all.filter { it.family == Family.DERIVED }

    @Test
    fun `backfilled unlock sets celebrated_at equal to unlocked_at (no celebration queued)`() {
        // Resolve GAMES_PLAYED_10 (threshold 10) to a qualifying value; everything else 0.
        val resolved = derivedDefs.associate { def ->
            def.id to if (def.id == "GAMES_PLAYED_10") 10 else 0
        }

        val plan = backfill.computeBackfillRows(derivedDefs, resolved, emptyMap(), now)

        val row = plan.rows.first { it.achievementId == "GAMES_PLAYED_10" }
        assertEquals(now, row.unlockedAt)
        assertEquals(
            "Backfilled unlock must mirror celebrated_at so it is NOT celebrated",
            row.unlockedAt, row.celebratedAt,
        )
    }

    @Test
    fun `non-qualifying derived achievement is persisted unlocked at null`() {
        val resolved = derivedDefs.associate { it.id to 0 }

        val plan = backfill.computeBackfillRows(derivedDefs, resolved, emptyMap(), now)

        val row = plan.rows.first { it.achievementId == "GAMES_PLAYED_10" }
        assertEquals(0, row.currentValue)
        assertEquals(0, row.tierReached)
        assertTrue(row.unlockedAt == null)
        assertTrue(row.celebratedAt == null)
    }

    @Test
    fun `existing unlock is preserved (unlocked_at not overwritten by backfill)`() {
        val originalUnlock = 500L
        val existing = mapOf(
            "GAMES_PLAYED_10" to AchievementProgressEntity(
                achievementId = "GAMES_PLAYED_10",
                currentValue = 10,
                tierReached = 1,
                unlockedAt = originalUnlock,
                celebratedAt = 500L,
            )
        )
        val resolved = derivedDefs.associate { def ->
            def.id to if (def.id == "GAMES_PLAYED_10") 25 else 0
        }

        val plan = backfill.computeBackfillRows(derivedDefs, resolved, existing, now)

        val row = plan.rows.first { it.achievementId == "GAMES_PLAYED_10" }
        // Value refreshed, but the real unlock + celebration stamps survive.
        assertEquals(25, row.currentValue)
        assertEquals(originalUnlock, row.unlockedAt)
        assertEquals(500L, row.celebratedAt)
        // No new tier crossed → no XP grant planned.
        assertTrue(plan.tierGrants.none { it.achievementId == "GAMES_PLAYED_10" })
    }

    @Test
    fun `backfill plans tier XP only for newly crossed tiers`() {
        // FOIL_COLLECTOR has tiers 10 and 50. Resolve to 60 with no prior → both tiers cross.
        val resolved = derivedDefs.associate { def ->
            def.id to if (def.id == "FOIL_COLLECTOR") 60 else 0
        }

        val plan = backfill.computeBackfillRows(derivedDefs, resolved, emptyMap(), now)

        val grants = plan.tierGrants.filter { it.achievementId == "FOIL_COLLECTOR" }
        assertEquals(2, grants.size)
        assertEquals(setOf(1, 2), grants.map { it.tier }.toSet())
    }

    @Test
    fun `every derived def yields exactly one row`() {
        val resolved = derivedDefs.associate { it.id to 0 }
        val plan = backfill.computeBackfillRows(derivedDefs, resolved, emptyMap(), now)
        assertEquals(derivedDefs.size, plan.rows.size)
    }

    @Test
    fun `run skips unavailable defs and resolves the rainbow pair separately`() = runTest {
        val dao = mockk<GamificationDao>(relaxed = true)
        val statsDao = mockk<GamificationStatsDao>(relaxed = true)
        coEvery { dao.getAchievement(any()) } returns null
        coEvery { dao.hasTransaction(any()) } returns true
        coEvery { statsDao.puzzlesSolved() } returns 30
        // One card of every color: the public rainbow unlocks, the 20-per-color secret does not.
        coEvery { statsDao.ownedCountForColor(any()) } returns 1
        val rows = mutableListOf<AchievementProgressEntity>()
        coEvery { dao.upsertAchievement(capture(rows)) } just Runs

        AchievementBackfill(dao, statsDao, FixedClock(Instant.fromEpochMilliseconds(now)), Dispatchers.Unconfined).run()

        assertEquals(!FeatureFlags.Puzzle.PUZZLE_ENABLED, rows.none { it.achievementId == "PUZZLE_SOLVER" })
        assertEquals(1, rows.first { it.achievementId == "RAINBOW_COLLECTOR" }.tierReached)
        assertEquals(0, rows.first { it.achievementId == "SECRET_PERFECT_RAINBOW" }.tierReached)
    }
}
