package com.mmg.manahub.feature.draft.domain.model

/**
 * Domain model representing a Magic: The Gathering set available for draft.
 * Sourced from the Cloudflare Worker sets-index.json endpoint.
 *
 * @property id Unique identifier — matches the set code (used as Room PK).
 * @property code Set code (e.g. "eoe").
 * @property name Human-readable set name.
 * @property releasedAt ISO-8601 release date string.
 * @property iconSvgUri Scryfall SVG URI for the set symbol.
 * @property guideVersion Content version string for the draft guide (date-based, e.g. "2025-08-08").
 * @property tierListVersion Content version string for the tier list.
 */
data class DraftSet(
    val id: String,
    val code: String,
    val name: String,
    val releasedAt: String,
    val iconSvgUri: String,
    val guideVersion: String,
    val tierListVersion: String,
)
