package com.mmg.manahub.core.model.news

/**
 * Outcome of a [com.mmg.manahub.core.domain.repository.NewsRepository.refreshAll] call, aggregated
 * across every source that was attempted (stale sources, or every enabled source when `force` was
 * requested). Lets the presentation layer surface partial-failure feedback ("Couldn't update N
 * source(s)") instead of a single opaque success/failure — one dead feed must never look like a
 * full failure, and a full failure must never silently look like success.
 *
 * @param fetched sources successfully fetched with a normal (200) response and parsed.
 * @param failed sources whose fetch/parse threw and were skipped for this refresh cycle.
 * @param notModified sources that returned HTTP 304 (conditional GET short-circuit) — no
 *  parse/upsert happened, only the watermark advanced.
 */
data class RefreshResult(
    val fetched: Int,
    val failed: Int,
    val notModified: Int,
) {
    /** Total number of sources this refresh cycle actually attempted to contact. */
    val attempted: Int get() = fetched + failed + notModified
}
