package com.mmg.manahub.core.model.news

import com.mmg.manahub.core.model.MagicSet
import kotlinx.datetime.LocalDate

enum class ReleaseStatus { UPCOMING, OUT_NOW }

/** A set release for MTG Today › Events; [daysUntil] is negative once released, 0 on release day. */
data class UpcomingRelease(
    val set: MagicSet,
    val releaseDate: LocalDate,
    val status: ReleaseStatus,
    val daysUntil: Int,
)

/** Upcoming/just-released sets plus the newest released main expansion (drives the Limited ratings link). */
data class ReleaseCalendar(
    val releases: List<UpcomingRelease>,
    val latestLimitedSet: MagicSet?,
)
