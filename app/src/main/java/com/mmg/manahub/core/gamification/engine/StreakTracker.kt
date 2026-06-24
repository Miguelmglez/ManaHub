package com.mmg.manahub.core.gamification.engine

import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.entity.StreakEntity
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.engine.StreakTracker.Companion.MAX_FREEZE_TOKENS
import com.mmg.manahub.core.gamification.engine.StreakTracker.Companion.TYPE_DAILY_ACTIVITY
import com.mmg.manahub.core.gamification.engine.StreakTracker.Companion.WEEK_LENGTH
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advances the daily-activity streak on [ProgressionEvent.AppOpenedToday] (ADR-002 §Context, Phase 2).
 *
 * Streaks use FREEZE TOKENS, never punishment: a single missed day (or several, if enough tokens are
 * banked) consumes tokens to PRESERVE the streak instead of resetting it. Tokens regenerate as the user
 * sustains activity (one per completed 7-day run, capped at [MAX_FREEZE_TOKENS]). The streak count never
 * drops below 1 and tokens never go negative or above the cap.
 *
 * The decision logic is the PURE [advance] function (existing row + today → new row) so the full matrix
 * is unit-testable without Room or a clock. [process] is the thin IO shell.
 */
@Singleton
class StreakTracker @Inject constructor(
    private val dao: GamificationDao,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {

    /**
     * On [ProgressionEvent.AppOpenedToday], advances the `daily_activity` streak for today's local date.
     * All other events are ignored.
     */
    suspend fun process(event: ProgressionEvent) {
        if (event !is ProgressionEvent.AppOpenedToday) return
        val today = clock.now().toLocalDateTime(timeZone).date
        val existing = dao.getStreak(TYPE_DAILY_ACTIVITY)
        dao.upsertStreak(advance(existing, today))
    }

    /**
     * Pure streak transition. Given the [existing] row (null if never seeded) and [today], returns the
     * new row. Freeze-token rules (cap [MAX_FREEZE_TOKENS]):
     *
     * - First ever (null): current = 1, longest = 1, tokens = [MAX_FREEZE_TOKENS].
     * - Same day (today == lastActiveDate): no-op (returns [existing] unchanged) — the streak counts
     *   consecutive CALENDAR DAYS, not app-open events, so repeated opens on the same local day never
     *   inflate `current`.
     * - Next day (gap 0 missed days): current + 1; tokens regenerate +1 (capped) when the new current
     *   is a multiple of 7 (a full active week completed).
     * - Gap of N >= 1 missed days: if tokens >= N, consume N and continue the streak (current + 1) —
     *   AND the same 7-day-multiple regen still applies, so a milestone token is never silently skipped
     *   just because the milestone day happened to be covered by a freeze; else reset current to 1,
     *   tokens unchanged (never negative).
     *
     * [type] defaults to [TYPE_DAILY_ACTIVITY] so the produced row carries the correct PK.
     */
    fun advance(existing: StreakEntity?, today: LocalDate, type: String = TYPE_DAILY_ACTIVITY): StreakEntity {
        if (existing == null) {
            return StreakEntity(
                type = type,
                current = 1,
                longest = 1,
                lastActiveDate = today.toString(),
                freezeTokens = MAX_FREEZE_TOKENS,
            )
        }

        val lastDate = runCatching { LocalDate.parse(existing.lastActiveDate) }.getOrNull()
        // Corrupt/empty stored date → treat as a fresh start today (defensive; never crash the engine).
            ?: return existing.copy(
                current = existing.current.coerceAtLeast(1),
                longest = existing.longest.coerceAtLeast(existing.current.coerceAtLeast(1)),
                lastActiveDate = today.toString(),
                freezeTokens = existing.freezeTokens.coerceIn(0, MAX_FREEZE_TOKENS),
            )

        val dayDelta = lastDate.daysUntil(today).toLong()

        return when {
            // Same day (or a clock that moved backwards) — no change.
            dayDelta <= 0L -> existing

            // Consecutive day: extend the streak; maybe regenerate a freeze token.
            dayDelta == 1L -> {
                val newCurrent = existing.current.coerceAtLeast(0) + 1
                existing.copy(
                    current = newCurrent,
                    longest = maxOf(existing.longest, newCurrent),
                    lastActiveDate = today.toString(),
                    freezeTokens = regenTokens(existing.freezeTokens, newCurrent),
                )
            }

            // A gap: today is lastActiveDate + (N + 1) days, so N missed days = dayDelta - 1.
            else -> {
                val missed = (dayDelta - 1L).toInt()
                if (existing.freezeTokens >= missed) {
                    // Freeze covers the gap: preserve and extend the streak. The milestone regen is
                    // applied to the POST-consumption balance with the SAME helper as the consecutive
                    // branch, so a 7-day-multiple token is never silently skipped when the milestone day
                    // is covered by a freeze (the two branches can't drift — symmetry by construction).
                    val newCurrent = existing.current.coerceAtLeast(0) + 1
                    existing.copy(
                        current = newCurrent,
                        longest = maxOf(existing.longest, newCurrent),
                        lastActiveDate = today.toString(),
                        freezeTokens = regenTokens(existing.freezeTokens - missed, newCurrent),
                    )
                } else {
                    // Not enough tokens: reset to a fresh 1-day streak (tokens unchanged, never negative).
                    existing.copy(
                        current = 1,
                        longest = existing.longest.coerceAtLeast(1),
                        lastActiveDate = today.toString(),
                        freezeTokens = existing.freezeTokens.coerceIn(0, MAX_FREEZE_TOKENS),
                    )
                }
            }
        }
    }

    /**
     * Applies the 7-day-milestone freeze-token regeneration to [baseTokens], clamped to the cap.
     *
     * Extracted so the consecutive-day and freeze-covered-gap branches use IDENTICAL regen logic and
     * cannot drift: a token is regenerated (+1) whenever [newCurrent] lands on a multiple of
     * [WEEK_LENGTH] (a full active week completed), then the total is clamped to `[0, MAX_FREEZE_TOKENS]`.
     */
    private fun regenTokens(baseTokens: Int, newCurrent: Int): Int {
        val regen = if (newCurrent % WEEK_LENGTH == 0) 1 else 0
        return (baseTokens + regen).coerceIn(0, MAX_FREEZE_TOKENS)
    }

    companion object {
        /** The single streak type tracked in this chunk. */
        const val TYPE_DAILY_ACTIVITY = "daily_activity"

        /** Maximum freeze tokens a streak can bank. */
        const val MAX_FREEZE_TOKENS = 2

        private const val WEEK_LENGTH = 7
    }
}
