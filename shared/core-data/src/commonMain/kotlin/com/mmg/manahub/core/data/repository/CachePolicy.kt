package com.mmg.manahub.core.data.repository

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Shared cache-freshness policy used by cache-first repository implementations.
 *
 * All thresholds are in milliseconds. [isFresh] / [isStale] compare the cached
 * timestamp against the current wall-clock via [Clock.System] (KMP-safe; replaces
 * the JVM-only `System.currentTimeMillis()`).
 */
@OptIn(ExperimentalTime::class)
object CachePolicy {
    const val FRESH_MS = 24L * 60 * 60 * 1_000        // 24 h
    const val STALE_MS = 7L  * 24 * 60 * 60 * 1_000   // 7 days  — show warning badge
    const val EVICT_MS = 30L * 24 * 60 * 60 * 1_000   // 30 days — evict uncollected cards

    fun isFresh(cachedAt: Long): Boolean =
        Clock.System.now().toEpochMilliseconds() - cachedAt < FRESH_MS

    fun isStale(cachedAt: Long): Boolean =
        Clock.System.now().toEpochMilliseconds() - cachedAt > STALE_MS
}
