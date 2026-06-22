package com.mmg.manahub.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for the Cloudflare Worker response at `/draft/sets-index.json`.
 * Represents the index of all available draft sets with version metadata.
 *
 * Lives in `:shared:core-data` `commonMain` — KMP-pure, uses `kotlinx.serialization`
 * instead of Gson. Field names match the old Gson DTOs so downstream mappers
 * (`DraftMappers.kt`) need only an import change.
 */
@Serializable
data class SetsIndexResponse(
    @SerialName("index_version") val indexVersion: String,
    @SerialName("last_updated") val lastUpdated: String,
    @SerialName("sets") val sets: List<SetIndexEntryDto>,
)

/**
 * DTO for a single set entry in the sets-index.json response.
 *
 * @property code Set code (e.g. "eoe").
 * @property name Full set name.
 * @property iconSvgUri Scryfall SVG icon URI.
 * @property releasedAt ISO-8601 release date.
 * @property contentVersions Version strings for guide and tier-list content.
 */
@Serializable
data class SetIndexEntryDto(
    @SerialName("code") val code: String,
    @SerialName("name") val name: String,
    @SerialName("icon_svg_uri") val iconSvgUri: String,
    @SerialName("released_at") val releasedAt: String,
    @SerialName("content_versions") val contentVersions: ContentVersionsDto,
)

/**
 * DTO carrying version strings for each content type within a set.
 * Used to decide whether a locally cached file needs to be refreshed.
 *
 * @property guide Version string for the draft guide (e.g. "2025-08-08").
 * @property tierList Version string for the tier list.
 * @property booster Present in the sets-index once a booster.json has been published for this set.
 */
@Serializable
data class ContentVersionsDto(
    @SerialName("guide") val guide: String,
    @SerialName("tier_list") val tierList: String,
    @SerialName("booster") val booster: String? = null,
)
