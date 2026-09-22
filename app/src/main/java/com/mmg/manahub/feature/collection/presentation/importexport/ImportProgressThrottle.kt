package com.mmg.manahub.feature.collection.presentation.importexport

import kotlinx.datetime.Clock

/**
 * Rate-limits the import resolver's progress callback. Resolution reports once per unmatched line,
 * which for a few thousand lines is a few thousand state updates and as many recompositions.
 *
 * The final call (`processed >= total`) always passes, so the bar still lands on an exact value.
 */
class ImportProgressThrottle(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private var lastEmittedAt = 0L
    private var lastEmittedPercent = -1

    fun shouldEmit(processed: Int, total: Int): Boolean {
        if (processed >= total) return true
        val percent = if (total == 0) 100 else processed * 100 / total
        val now = nowMillis()
        if (percent == lastEmittedPercent || now - lastEmittedAt < minIntervalMs) return false
        lastEmittedAt = now
        lastEmittedPercent = percent
        return true
    }

    companion object {
        const val DEFAULT_MIN_INTERVAL_MS = 250L
    }
}
