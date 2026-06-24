package com.mmg.manahub.core.gamification.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the PURE [QuestPeriodKeys] (ADR-002 §9): key formats, ISO week-based-year boundary
 * behaviour, and expiry = next-period start.
 */
class QuestPeriodKeysTest {

    private val utc = TimeZone.UTC

    // ── Key formats ───────────────────────────────────────────────────────────────

    @Test
    fun `daily key is the ISO local date`() {
        assertEquals("2026-06-12", QuestPeriodKeys.dailyKey(LocalDate(2026, 6, 12)))
    }

    @Test
    fun `weekly key is ISO week-based year and week, zero-padded`() {
        // 2026-06-12 is a Friday in ISO week 24 of 2026.
        assertEquals("2026-W24", QuestPeriodKeys.weeklyKey(LocalDate(2026, 6, 12)))
        // Early-year week is zero-padded to two digits. (2026-01-05 is the Monday starting ISO week 2,
        // since ISO week 1 of 2026 began on 2025-12-29.)
        assertEquals("2026-W02", QuestPeriodKeys.weeklyKey(LocalDate(2026, 1, 5)))
    }

    @Test
    fun `previous day and week helpers step back one period`() {
        assertEquals("2026-06-11", QuestPeriodKeys.previousDailyKey(LocalDate(2026, 6, 12)))
        assertEquals("2026-W23", QuestPeriodKeys.previousWeeklyKey(LocalDate(2026, 6, 12)))
    }

    // ── ISO week-based-year boundary ──────────────────────────────────────────────

    @Test
    fun `ISO week-based year differs from calendar year at the year boundary`() {
        // 2026-12-31 is a Thursday → ISO week 53 of week-based-year 2026.
        assertEquals("2026-W53", QuestPeriodKeys.weeklyKey(LocalDate(2026, 12, 31)))
        // 2027-01-01 is a Friday → still ISO week 53 of week-based-year 2026 (NOT 2027-W01).
        assertEquals("2026-W53", QuestPeriodKeys.weeklyKey(LocalDate(2027, 1, 1)))
        // 2027-01-04 is the Monday that starts ISO week 1 of 2027.
        assertEquals("2027-W01", QuestPeriodKeys.weeklyKey(LocalDate(2027, 1, 4)))
    }

    @Test
    fun `dates in the same ISO week map to the same weekly key`() {
        // Monday 2026-06-08 .. Sunday 2026-06-14 are all ISO week 24.
        val monday = LocalDate(2026, 6, 8)
        for (offset in 0..6) {
            assertEquals("2026-W24", QuestPeriodKeys.weeklyKey(monday.plus(offset, DateTimeUnit.DAY)))
        }
    }

    // ── Expiry = next-period start ────────────────────────────────────────────────

    @Test
    fun `daily expiry is the start of the next local day`() {
        val date = LocalDate(2026, 6, 12)
        val expected = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(utc).toEpochMilliseconds()
        assertEquals(expected, QuestPeriodKeys.dailyExpiresAt(date, utc))
    }

    @Test
    fun `weekly expiry is the Monday that begins the next ISO week`() {
        // 2026-06-12 (Fri, week 24) → next ISO week starts Monday 2026-06-15 00:00.
        val date = LocalDate(2026, 6, 12)
        val expectedMonday = LocalDate(2026, 6, 15).atStartOfDayIn(utc).toEpochMilliseconds()
        assertEquals(expectedMonday, QuestPeriodKeys.weeklyExpiresAt(date, utc))
    }

    @Test
    fun `weekly expiry from a Monday advances a full week`() {
        // 2026-06-08 is a Monday → expiry is the next Monday 2026-06-15.
        val monday = LocalDate(2026, 6, 8)
        val expectedNextMonday = LocalDate(2026, 6, 15).atStartOfDayIn(utc).toEpochMilliseconds()
        assertEquals(expectedNextMonday, QuestPeriodKeys.weeklyExpiresAt(monday, utc))
    }
}
