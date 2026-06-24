package com.mmg.manahub.core.gamification.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Pure helpers that turn a [LocalDate] into the stable `periodKey` strings used by
 * [com.mmg.manahub.core.data.local.entity.QuestInstanceEntity] and to compute expiry boundaries
 * (ADR-002 §9).
 *
 * ### Determinism
 * Weekly keys use the ISO-8601 week-based year/week formatted as `"%04d-W%02d"` — NOT
 * `WeekFields.of(Locale)`, whose first-day-of-week and minimal-days rules are locale-dependent and
 * would make the same instant resolve to different weeks on different devices. This stability is what
 * lets two devices on the same account independently generate the SAME quests for a period with zero
 * sync coordination (ADR-002 §9/§11).
 *
 * All functions are pure (a [LocalDate] / [TimeZone] in, a value out) so they are unit-testable without
 * Android. Callers in the engine derive the [LocalDate] from an injected `Clock` + `TimeZone`.
 */
object QuestPeriodKeys {

    /** Daily key: the ISO local date `yyyy-MM-dd`. */
    fun dailyKey(date: LocalDate): String = date.toString()

    /**
     * ISO week-based key `"YYYY-Www"` (e.g. `"2026-W24"`). Week-based year, not calendar year.
     *
     * Uses the Thursday-based algorithm: the ISO week-based year of a date is the year of the
     * Thursday in that date's ISO week, and the week number is derived from the ordinal day of
     * that Thursday within its year.
     */
    fun weeklyKey(date: LocalDate): String {
        val dayOfWeek = date.dayOfWeek.isoDayNumber // 1=Mon..7=Sun
        // Find the Thursday of this date's week (ISO weeks are defined by their Thursday)
        val thursday = date.plus(4 - dayOfWeek, DateTimeUnit.DAY)
        // The ISO week-based year is the year of that Thursday
        val weekYear = thursday.year
        // Week number = (ordinal day of Thursday + 6) / 7
        val jan1 = LocalDate(weekYear, 1, 1)
        val ordinal = thursday.toEpochDays() - jan1.toEpochDays() + 1
        val week = (ordinal + 6) / 7
        return "${weekYear.toString().padStart(4, '0')}-W${week.toString().padStart(2, '0')}"
    }

    /** Daily key for the previous local day (used by the no-repeat-yesterday rule). */
    fun previousDailyKey(date: LocalDate): String = dailyKey(date.minus(1, DateTimeUnit.DAY))

    /** ISO-week key for the previous week (used by the no-repeat-last-week rule). */
    fun previousWeeklyKey(date: LocalDate): String = weeklyKey(date.minus(7, DateTimeUnit.DAY))

    /**
     * Epoch-millis at the start of the NEXT local day (i.e. when "today's" daily quests expire).
     * Computed in [zone] so the boundary aligns with the device's local midnight.
     */
    fun dailyExpiresAt(date: LocalDate, zone: TimeZone): Long =
        date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()

    /**
     * Epoch-millis at the start of the NEXT ISO week (Monday 00:00 local), i.e. when "this week's"
     * weekly quests expire. ISO weeks start on Monday, so we advance to the Monday strictly after
     * [date].
     */
    fun weeklyExpiresAt(date: LocalDate, zone: TimeZone): Long {
        val nextMonday = nextIsoWeekStart(date)
        return nextMonday.atStartOfDayIn(zone).toEpochMilliseconds()
    }

    /** The Monday that begins the ISO week STRICTLY after the week containing [date]. */
    private fun nextIsoWeekStart(date: LocalDate): LocalDate {
        // ISO day-of-week: Monday = 1 .. Sunday = 7. Monday of the current week:
        val mondayThisWeek = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        return mondayThisWeek.plus(7, DateTimeUnit.DAY)
    }
}
