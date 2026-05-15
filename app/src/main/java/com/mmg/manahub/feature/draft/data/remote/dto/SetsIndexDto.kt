package com.mmg.manahub.feature.draft.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO for the Cloudflare Worker response at /draft/sets-index.json.
 * Represents the index of all available draft sets with version metadata.
 */
data class SetsIndexResponse(
    @SerializedName("index_version") val indexVersion: String,
    @SerializedName("last_updated") val lastUpdated: String,
    @SerializedName("sets") val sets: List<SetIndexEntryDto>,
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
data class SetIndexEntryDto(
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("icon_svg_uri") val iconSvgUri: String,
    @SerializedName("released_at") val releasedAt: String,
    @SerializedName("content_versions") val contentVersions: ContentVersionsDto,
)

/**
 * DTO carrying version strings for each content type within a set.
 * Used to decide whether a locally cached file needs to be refreshed.
 *
 * @property guide Version string for the draft guide (e.g. "2025-08-08").
 * @property tierList Version string for the tier list.
 */
data class ContentVersionsDto(
    @SerializedName("guide") val guide: String,
    @SerializedName("tier_list") val tierList: String,
)
