package com.mmg.manahub.core.gamification.domain

import kotlin.math.pow
import kotlin.math.roundToLong


/**
 * The XP → level curve for player progression (ADR-002 §6).
 *
 * - [xpToAdvance] is the canonical formula: `round(100 * level^1.5)` XP to go from
 *   `level` to `level + 1`.
 * - Cumulative totals for levels 1..[PRECOMPUTED_LEVELS] are precomputed once into an
 *   immutable list for cheap lookups; beyond that they are computed lazily by extending
 *   the same formula. **There is no level cap.**
 * - The table MUST equal the formula at every precomputed level (asserted by tests). This
 *   formula is intentionally duplicated server-side in Phase 4 (`batch_upsert_xp_transactions`)
 *   — keep both in sync if the constants change.
 *
 * Pure and deterministic, so it lives as an `object` (no injection, trivially testable).
 */
object LevelCurve {

    /** First level a player has. */
    const val MIN_LEVEL: Int = 1

    /** Number of levels precomputed into [cumulativeXpForLevel]. Beyond this, computed lazily. */
    const val PRECOMPUTED_LEVELS: Int = 60

    /** XP required to advance FROM [level] to [level] + 1. `level` is clamped to >= [MIN_LEVEL]. */
    fun xpToAdvance(level: Int): Long {
        val effectiveLevel = level.coerceAtLeast(MIN_LEVEL)
        return (100.0 * effectiveLevel.toDouble().pow(1.5)).roundToLong()
    }

    /**
     * Cumulative total XP required to REACH a given level, indexed by level.
     *
     * `cumulativeXpForLevel[1] == 0` (you start at level 1 with 0 XP); `cumulativeXpForLevel[n]`
     * is the sum of [xpToAdvance] for levels 1..n-1. Index 0 is padding (unused / 0) so the
     * list is directly indexable by level.
     */
    val cumulativeXpForLevel: List<Long> = buildList(PRECOMPUTED_LEVELS + 1) {
        add(0L) // index 0 — padding, never a real level
        var cumulative = 0L
        add(0L) // level 1 — reached at 0 XP
        for (level in MIN_LEVEL until PRECOMPUTED_LEVELS) {
            cumulative += xpToAdvance(level)
            add(cumulative) // cumulative XP to reach level + 1
        }
    }

    /**
     * Returns the level a player with [totalXp] has reached. Never below [MIN_LEVEL].
     * For totals beyond the precomputed table the curve is extended lazily.
     */
    fun levelForTotalXp(totalXp: Long): Int {
        if (totalXp <= 0L) return MIN_LEVEL

        // Fast path: walk the precomputed cumulative table. `level` may reach PRECOMPUTED_LEVELS.
        var level = MIN_LEVEL
        while (level + 1 <= PRECOMPUTED_LEVELS && cumulativeXpForLevel[level + 1] <= totalXp) {
            level++
        }
        // If we did not exhaust the table, `level` is already the answer.
        if (level < PRECOMPUTED_LEVELS) return level

        // Slow path: the player is at or beyond the last precomputed level — extend lazily.
        var cumulative = cumulativeXpForLevel[PRECOMPUTED_LEVELS]
        var currentLevel = PRECOMPUTED_LEVELS
        while (true) {
            val next = cumulative + xpToAdvance(currentLevel)
            if (next > totalXp) return currentLevel
            cumulative = next
            currentLevel++
        }
    }

    /**
     * Splits [totalXp] into progress within the current level.
     *
     * @return `(xpIntoCurrentLevel, xpNeededForNextLevel)` where the first is how far into the
     *   current level the player is, and the second is the total XP span of the current level
     *   ([xpToAdvance] of the current level).
     */
    fun xpIntoCurrentLevel(totalXp: Long): Pair<Long, Long> {
        val clampedTotal = totalXp.coerceAtLeast(0L)
        val level = levelForTotalXp(clampedTotal)
        val floor = cumulativeXpForReachedLevel(level)
        val needed = xpToAdvance(level)
        val into = (clampedTotal - floor).coerceIn(0L, needed)
        return into to needed
    }

    /**
     * Cumulative XP required to reach [level], using the precomputed table when possible and
     * extending lazily beyond it.
     */
    private fun cumulativeXpForReachedLevel(level: Int): Long {
        val effective = level.coerceAtLeast(MIN_LEVEL)
        if (effective <= PRECOMPUTED_LEVELS) return cumulativeXpForLevel[effective]
        var cumulative = cumulativeXpForLevel[PRECOMPUTED_LEVELS]
        for (l in PRECOMPUTED_LEVELS until effective) {
            cumulative += xpToAdvance(l)
        }
        return cumulative
    }
}
