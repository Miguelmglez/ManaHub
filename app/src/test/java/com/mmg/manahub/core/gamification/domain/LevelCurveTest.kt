package com.mmg.manahub.core.gamification.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LevelCurve]: the precomputed cumulative table must equal the formula, levels
 * must be monotonic, threshold boundaries must resolve correctly, and the curve must extend
 * lazily beyond the precomputed cap.
 */
class LevelCurveTest {

    @Test
    fun `xpToAdvance matches the round(100 * level^1_5) formula`() {
        for (level in 1..LevelCurve.PRECOMPUTED_LEVELS) {
            val expected = Math.round(100.0 * Math.pow(level.toDouble(), 1.5))
            assertEquals("xpToAdvance($level)", expected, LevelCurve.xpToAdvance(level))
        }
    }

    @Test
    fun `xpToAdvance clamps levels below 1 to level 1`() {
        assertEquals(LevelCurve.xpToAdvance(1), LevelCurve.xpToAdvance(0))
        assertEquals(LevelCurve.xpToAdvance(1), LevelCurve.xpToAdvance(-5))
    }

    @Test
    fun `cumulative table equals the running sum of the formula for 1 to 60`() {
        // Level 1 is reached at 0 XP.
        assertEquals(0L, LevelCurve.cumulativeXpForLevel[1])
        var running = 0L
        for (level in 1 until LevelCurve.PRECOMPUTED_LEVELS) {
            running += LevelCurve.xpToAdvance(level)
            assertEquals(
                "cumulative to reach level ${level + 1}",
                running,
                LevelCurve.cumulativeXpForLevel[level + 1],
            )
        }
    }

    @Test
    fun `cumulative table is strictly increasing from level 1 onward`() {
        for (level in 1 until LevelCurve.PRECOMPUTED_LEVELS) {
            assertTrue(
                "cumulative must increase from level $level to ${level + 1}",
                LevelCurve.cumulativeXpForLevel[level + 1] > LevelCurve.cumulativeXpForLevel[level],
            )
        }
    }

    @Test
    fun `levelForTotalXp returns level 1 for zero or negative xp`() {
        assertEquals(1, LevelCurve.levelForTotalXp(0L))
        assertEquals(1, LevelCurve.levelForTotalXp(-100L))
    }

    @Test
    fun `levelForTotalXp resolves exact cumulative thresholds to the new level`() {
        // At exactly the cumulative XP to reach level N, the player IS level N (not N-1).
        for (level in 2..LevelCurve.PRECOMPUTED_LEVELS) {
            val threshold = LevelCurve.cumulativeXpForLevel[level]
            assertEquals("at exact threshold for level $level", level, LevelCurve.levelForTotalXp(threshold))
            // One XP below the threshold is still the previous level.
            assertEquals(
                "just below threshold for level $level",
                level - 1,
                LevelCurve.levelForTotalXp(threshold - 1),
            )
        }
    }

    @Test
    fun `levelForTotalXp is monotonic non-decreasing across a wide range`() {
        var previous = 1
        var xp = 0L
        while (xp <= LevelCurve.cumulativeXpForLevel[LevelCurve.PRECOMPUTED_LEVELS]) {
            val level = LevelCurve.levelForTotalXp(xp)
            assertTrue("level must not decrease as xp grows", level >= previous)
            previous = level
            xp += 137L // arbitrary step to sample the range
        }
    }

    @Test
    fun `levelForTotalXp extends lazily beyond the precomputed table`() {
        // Cumulative XP to reach level 61 = table[60] + xpToAdvance(60).
        val xpForLevel61 = LevelCurve.cumulativeXpForLevel[LevelCurve.PRECOMPUTED_LEVELS] +
            LevelCurve.xpToAdvance(LevelCurve.PRECOMPUTED_LEVELS)
        assertEquals(61, LevelCurve.levelForTotalXp(xpForLevel61))
        assertEquals(60, LevelCurve.levelForTotalXp(xpForLevel61 - 1))

        // And a far-beyond value resolves to a level > 61 (no cap).
        assertTrue(LevelCurve.levelForTotalXp(xpForLevel61 * 4) > 61)
    }

    @Test
    fun `xpIntoCurrentLevel splits total into progress and span`() {
        // 10 XP into level 1 (level 1 floor is 0).
        val (into1, needed1) = LevelCurve.xpIntoCurrentLevel(10L)
        assertEquals(10L, into1)
        assertEquals(LevelCurve.xpToAdvance(1), needed1)

        // Exactly at the level-2 threshold => 0 into level 2, span = xpToAdvance(2).
        val level2Floor = LevelCurve.cumulativeXpForLevel[2]
        val (into2, needed2) = LevelCurve.xpIntoCurrentLevel(level2Floor)
        assertEquals(0L, into2)
        assertEquals(LevelCurve.xpToAdvance(2), needed2)

        // Halfway-ish into level 3.
        val level3Floor = LevelCurve.cumulativeXpForLevel[3]
        val (into3, needed3) = LevelCurve.xpIntoCurrentLevel(level3Floor + 5L)
        assertEquals(5L, into3)
        assertEquals(LevelCurve.xpToAdvance(3), needed3)
    }
}
