package com.mmg.manahub.core.util

import kotlinx.datetime.Clock

/**
 * English-only relative date formatter.
 *
 * Converts an epoch-millis timestamp into a compact, human-readable "time ago"
 * string (e.g. "3h ago", "2d ago"). This project is English-only — locale
 * branches were intentionally removed per project language rules.
 *
 * Lives in `:shared:core-model` commonMain so it can be used from both
 * Android and Web (wasmJs) targets.
 */
object TimeAgoFormatter {

    /**
     * Format [epochMillis] as a relative "time ago" string.
     *
     * @param epochMillis UTC epoch milliseconds of the event. Values <= 0 or in
     *                    the future return an empty string.
     * @return A compact English string like "now", "5m ago", "2d ago", or "" for
     *         invalid/future timestamps.
     */
    fun format(epochMillis: Long): String {
        if (epochMillis <= 0L) return ""
        val now = Clock.System.now().toEpochMilliseconds()
        val diff = now - epochMillis
        if (diff < 0) return ""

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val weeks = days / 7
        val months = days / 30
        val years = days / 365

        return when {
            seconds < 60 -> "now"
            minutes < 60 -> formatUnit(minutes, "m")
            hours < 24   -> formatUnit(hours, "h")
            days < 7     -> formatUnit(days, "d")
            weeks < 5    -> formatUnit(weeks, "w")
            months < 12  -> formatUnit(months, "mo")
            else         -> formatUnit(years, "y")
        }
    }

    private fun formatUnit(value: Long, unit: String): String = "${value}${unit} ago"
}
