package com.mmg.manahub.feature.news.domain.events

import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.SetType
import com.mmg.manahub.core.model.news.ReleaseCalendar
import com.mmg.manahub.core.model.news.ReleaseStatus
import com.mmg.manahub.core.model.news.UpcomingRelease
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

object ReleaseCalendarBuilder {

    const val MAX_RELEASES = 8
    const val MAX_OUT_NOW = 3
    const val OUT_NOW_WINDOW_DAYS = 14

    /** Up to [MAX_OUT_NOW] sets released in the last [OUT_NOW_WINDOW_DAYS] days, then upcoming ones, oldest first. */
    fun build(sets: List<MagicSet>, today: LocalDate): ReleaseCalendar {
        val dated = sets.mapNotNull { set -> set.releasedAt?.let { parseDate(it) }?.let { set to it } }
        val releases = dated.mapNotNull { (set, date) ->
            val days = today.daysUntil(date)
            when {
                days > 0 -> UpcomingRelease(set, date, ReleaseStatus.UPCOMING, days)
                days >= -OUT_NOW_WINDOW_DAYS -> UpcomingRelease(set, date, ReleaseStatus.OUT_NOW, days)
                else -> null
            }
        }
        val outNow = releases
            .filter { it.status == ReleaseStatus.OUT_NOW }
            .sortedByDescending { it.releaseDate }
            .take(MAX_OUT_NOW)
            .sortedBy { it.releaseDate }
        val upcoming = releases.filter { it.status == ReleaseStatus.UPCOMING }.sortedBy { it.releaseDate }
        val latestLimitedSet = dated
            .filter { (set, date) -> set.setType == SetType.EXPANSION && date <= today }
            .maxByOrNull { (_, date) -> date }
            ?.first
        return ReleaseCalendar(releases = (outNow + upcoming).take(MAX_RELEASES), latestLimitedSet = latestLimitedSet)
    }

    private fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (_: IllegalArgumentException) {
        null
    }
}
