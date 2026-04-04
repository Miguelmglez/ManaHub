package com.mmg.magicfolder.feature.news.presentation

import java.util.Locale

object TimeAgoFormatter {

    fun format(epochMillis: Long, locale: Locale = Locale.getDefault()): String {
        if (epochMillis <= 0L) return ""
        val now = System.currentTimeMillis()
        val diff = now - epochMillis
        if (diff < 0) return ""

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val weeks = days / 7
        val months = days / 30
        val years = days / 365

        val lang = locale.language
        return when {
            seconds < 60  -> when (lang) { "es" -> "ahora"; "de" -> "jetzt"; else -> "now" }
            minutes < 60  -> formatUnit(minutes, "m", lang)
            hours < 24    -> formatUnit(hours, "h", lang)
            days < 7      -> formatUnit(days, "d", lang)
            weeks < 5     -> formatUnit(weeks, "w", lang)
            months < 12   -> formatUnit(months, "mo", lang)
            else          -> formatUnit(years, "y", lang)
        }
    }

    private fun formatUnit(value: Long, unit: String, lang: String): String = when (lang) {
        "es" -> when (unit) {
            "m"  -> "hace ${value}min"
            "h"  -> "hace ${value}h"
            "d"  -> "hace ${value}d"
            "w"  -> "hace ${value}sem"
            "mo" -> "hace ${value}mes"
            "y"  -> "hace ${value}a"
            else -> "hace $value$unit"
        }
        "de" -> when (unit) {
            "m"  -> "vor ${value} Min."
            "h"  -> "vor ${value} Std."
            "d"  -> "vor ${value} T."
            "w"  -> "vor ${value} Wo."
            "mo" -> "vor ${value} Mon."
            "y"  -> "vor ${value} J."
            else -> "vor $value$unit"
        }
        else -> "${value}${unit} ago"
    }
}
