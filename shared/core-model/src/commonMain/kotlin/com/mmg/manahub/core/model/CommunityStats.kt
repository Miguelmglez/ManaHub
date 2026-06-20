package com.mmg.manahub.core.model

/**
 * Aggregated, anonymised community statistics surfaced by the Home community
 * widgets (top commanders, meta breakdown, most-wishlisted cards, milestones).
 *
 * This is a forward-looking model: the backing data source is not yet built, so
 * the repository currently emits `null`. The widgets render a loading state while
 * the value is null and an empty/error state when the lists are empty.
 */
data class CommunityStats(
    val topCommanders: List<CommunityEntry>,
    val metaArchetypes: List<CommunityEntry>,
    val mostWishlisted: List<CommunityEntry>,
    val milestones: List<CommunityMilestone>,
)

/** A single ranked community row (e.g. a commander with its play share). */
data class CommunityEntry(
    val id: String,
    val name: String,
    val count: Int,
    val percentage: Double,
)

/** A community-wide milestone headline (e.g. "1M cards tracked"). */
data class CommunityMilestone(
    val id: String,
    val label: String,
    val value: String,
)
