package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.entity.StreakEntity
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit tests for the PURE [StreakTracker.advance] (ADR-002 §Context, Phase 2): same-day no-op,
 * consecutive +1 with longest tracking, single-miss consuming a freeze token, multi-miss with
 * insufficient tokens resetting, token regen on a 7-day multiple, and the never-negative / first-ever
 * invariants.
 */
class StreakTrackerTest {

    private lateinit var tracker: StreakTracker

    private val today: LocalDate = LocalDate.of(2026, 6, 12)

    @Before
    fun setUp() {
        // The DAO/clock/zone are irrelevant for the pure `advance`; supply stubs so we can construct.
        tracker = StreakTracker(
            mockk<GamificationDao>(relaxed = true),
            Clock.fixed(Instant.parse("2026-06-12T10:00:00Z"), ZoneId.of("UTC")),
            ZoneId.of("UTC"),
        )
    }

    private fun streak(current: Int, longest: Int, lastDate: LocalDate, tokens: Int) =
        StreakEntity(
            type = StreakTracker.TYPE_DAILY_ACTIVITY,
            current = current, longest = longest,
            lastActiveDate = lastDate.toString(), freezeTokens = tokens,
        )

    // ── First-ever ────────────────────────────────────────────────────────────────

    @Test
    fun `first ever seeds current 1 longest 1 with max tokens`() {
        val result = tracker.advance(existing = null, today = today)
        assertEquals(1, result.current)
        assertEquals(1, result.longest)
        assertEquals(StreakTracker.MAX_FREEZE_TOKENS, result.freezeTokens)
        assertEquals(today.toString(), result.lastActiveDate)
    }

    // ── Same day ──────────────────────────────────────────────────────────────────

    @Test
    fun `same day is a no-op returning the existing row unchanged`() {
        val existing = streak(current = 4, longest = 9, lastDate = today, tokens = 1)
        val result = tracker.advance(existing, today)
        assertSame(existing, result)
    }

    // ── Consecutive day ───────────────────────────────────────────────────────────

    @Test
    fun `consecutive day increments current and tracks longest`() {
        val existing = streak(current = 3, longest = 3, lastDate = today.minusDays(1), tokens = 0)
        val result = tracker.advance(existing, today)
        assertEquals(4, result.current)
        assertEquals(4, result.longest)
    }

    @Test
    fun `consecutive day does not lower an already-higher longest`() {
        val existing = streak(current = 2, longest = 10, lastDate = today.minusDays(1), tokens = 0)
        val result = tracker.advance(existing, today)
        assertEquals(3, result.current)
        assertEquals(10, result.longest)
    }

    @Test
    fun `reaching a 7-day multiple regenerates one freeze token (capped)`() {
        // current 6 → 7 (a multiple of 7) regenerates a token, capped at MAX.
        val existing = streak(current = 6, longest = 6, lastDate = today.minusDays(1), tokens = 0)
        val result = tracker.advance(existing, today)
        assertEquals(7, result.current)
        assertEquals(1, result.freezeTokens)
    }

    @Test
    fun `token regen never exceeds the cap`() {
        val existing = streak(current = 6, longest = 6, lastDate = today.minusDays(1),
            tokens = StreakTracker.MAX_FREEZE_TOKENS)
        val result = tracker.advance(existing, today)
        assertEquals(StreakTracker.MAX_FREEZE_TOKENS, result.freezeTokens)
    }

    @Test
    fun `a non-multiple-of-7 consecutive day does not regenerate a token`() {
        val existing = streak(current = 4, longest = 4, lastDate = today.minusDays(1), tokens = 0)
        val result = tracker.advance(existing, today)
        assertEquals(5, result.current)
        assertEquals(0, result.freezeTokens)
    }

    // ── Single miss → consume one token ───────────────────────────────────────────

    @Test
    fun `a single missed day consumes one freeze token and preserves the streak`() {
        // last active 2 days ago → 1 missed day. tokens 2 → 1.
        val existing = streak(current = 5, longest = 5, lastDate = today.minusDays(2), tokens = 2)
        val result = tracker.advance(existing, today)
        assertEquals(6, result.current)
        assertEquals(1, result.freezeTokens)
    }

    // ── Multi-miss with insufficient tokens → reset ───────────────────────────────

    @Test
    fun `multiple missed days beyond available tokens resets the streak to one`() {
        // last active 4 days ago → 3 missed days, only 2 tokens → reset.
        val existing = streak(current = 12, longest = 12, lastDate = today.minusDays(4), tokens = 2)
        val result = tracker.advance(existing, today)
        assertEquals(1, result.current)
        assertEquals(12, result.longest) // longest is preserved
        assertTrue("tokens never go negative", result.freezeTokens >= 0)
    }

    @Test
    fun `a gap exactly covered by tokens preserves the streak and zeroes the tokens`() {
        // 2 missed days, exactly 2 tokens → consume both, continue.
        val existing = streak(current = 8, longest = 8, lastDate = today.minusDays(3), tokens = 2)
        val result = tracker.advance(existing, today)
        assertEquals(9, result.current)
        assertEquals(0, result.freezeTokens)
    }

    // ── Invariants ────────────────────────────────────────────────────────────────

    @Test
    fun `current never drops below one and tokens never go negative on reset`() {
        val existing = streak(current = 1, longest = 1, lastDate = today.minusDays(10), tokens = 0)
        val result = tracker.advance(existing, today)
        assertTrue(result.current >= 1)
        assertTrue(result.freezeTokens >= 0)
    }

    @Test
    fun `a corrupt stored date is treated defensively as a fresh start today`() {
        val corrupt = StreakEntity(
            type = StreakTracker.TYPE_DAILY_ACTIVITY, current = 5, longest = 7,
            lastActiveDate = "not-a-date", freezeTokens = 1,
        )
        val result = tracker.advance(corrupt, today)
        assertEquals(today.toString(), result.lastActiveDate)
        assertTrue(result.current >= 1)
        assertTrue(result.freezeTokens in 0..StreakTracker.MAX_FREEZE_TOKENS)
    }

    @Test
    fun `a clock that moved backwards is treated as same-day no-op`() {
        // today is BEFORE lastActiveDate (dayDelta < 0) → no change.
        val existing = streak(current = 4, longest = 4, lastDate = today.plusDays(2), tokens = 1)
        val result = tracker.advance(existing, today)
        assertSame(existing, result)
    }
}
