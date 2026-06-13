package com.mmg.manahub.core.gamification.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields

/**
 * Pure helpers that turn a [LocalDate] into the stable `periodKey` strings used by
 * [com.mmg.manahub.core.data.local.entity.QuestInstanceEntity] and to compute expiry boundaries
 * (ADR-002 §9).
 *
 * ### Determinism
 * Weekly keys use ISO-8601 week-based year/week ([IsoFields]) formatted as `"%04d-W%02d"` — NOT
 * `WeekFields.of(Locale)`, whose first-day-of-week and minimal-days rules are locale-dependent and
 * would make the same instant resolve to different weeks on different devices. This stability is what
 * lets two devices on the same account independently generate the SAME quests for a period with zero
 * sync coordination (ADR-002 §9/§11).
 *
 * All functions are pure (a [LocalDate] / [ZoneId] in, a value out) so they are unit-testable without
 * Android. Callers in the engine derive the [LocalDate] from an injected `Clock` + `ZoneId`.
 */
object QuestPeriodKeys {

    /** Daily key: the ISO local date `yyyy-MM-dd`. */
    fun dailyKey(date: LocalDate): String = date.toString()

    /** ISO week-based key `"YYYY-Www"` (e.g. `"2026-W24"`). Week-based year, not calendar year. */
    fun weeklyKey(date: LocalDate): String {
        val weekYear = date.get(IsoFields.WEEK_BASED_YEAR)
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return "%04d-W%02d".format(weekYear, week)
    }

    /** Daily key for the previous local day (used by the no-repeat-yesterday rule). */
    fun previousDailyKey(date: LocalDate): String = dailyKey(date.minusDays(1))

    /** ISO-week key for the previous week (used by the no-repeat-last-week rule). */
    fun previousWeeklyKey(date: LocalDate): String = weeklyKey(date.minusWeeks(1))

    /**
     * Epoch-millis at the start of the NEXT local day (i.e. when "today's" daily quests expire).
     * Computed in [zone] so the boundary aligns with the device's local midnight.
     */
    fun dailyExpiresAt(date: LocalDate, zone: ZoneId): Long =
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * Epoch-millis at the start of the NEXT ISO week (Monday 00:00 local), i.e. when "this week's"
     * weekly quests expire. ISO weeks start on Monday, so we advance to the Monday strictly after
     * [date].
     */
    fun weeklyExpiresAt(date: LocalDate, zone: ZoneId): Long {
        val nextMonday = nextIsoWeekStart(date)
        return nextMonday.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** The Monday that begins the ISO week STRICTLY after the week containing [date]. */
    private fun nextIsoWeekStart(date: LocalDate): LocalDate {
        // ISO day-of-week: Monday = 1 .. Sunday = 7. Monday of the current week:
        val mondayThisWeek = date.minusDays((date.dayOfWeek.value - 1).toLong())
        return mondayThisWeek.plusWeeks(1)
    }
}
