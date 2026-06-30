package com.mmg.manahub.core.domain.usecase.card

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Private JSON codec for the compact tag persistence format `[{"k":"key","c":"CATEGORY"}]`.
 *
 * This is a `commonMain` replacement for the Gson-backed `toTagList()` that lived in
 * `CardEntityMapper.kt` (`:app`). The entity mapper in `:app` retains its own Gson
 * implementation; this codec is exclusively for [ComputeCardTagsUseCase].
 */
@Serializable
private data class TagRecord(
    @SerialName("k") val k: String,
    @SerialName("c") val c: String,
)

private val tagJson = Json { ignoreUnknownKeys = true }

/**
 * Parses a compact tag JSON array into a list of [CardTag].
 *
 * Blank or malformed input returns an empty list (never throws).
 * Entries with an unrecognised category name fall back to [TagCategory.CUSTOM].
 */
internal fun String.toTagList(): List<CardTag> {
    if (isBlank() || this == "[]") return emptyList()
    return runCatching {
        tagJson.decodeFromString<List<TagRecord>>(this).mapNotNull { record ->
            val category = runCatching { TagCategory.valueOf(record.c) }.getOrDefault(TagCategory.CUSTOM)
            CardTag(key = record.k, category = category)
        }
    }.getOrDefault(emptyList())
}
