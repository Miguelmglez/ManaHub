package com.mmg.manahub.tools.tagpipeline.scryfall

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One line of Scryfall's `oracle_tags` bulk-data file (`.jsonl.gz`, Tagger community project —
 * `scryfall.com/docs/api/tags`). Each line is a self-contained tag object carrying its own
 * [taggings] — there is NO separate join file, unlike a first guess at the shape might suggest.
 *
 * **Verified against a real downloaded sample** (2026-07-20, `curl
 * https://data.scryfall.io/oracle-tags/oracle-tags-<date>.jsonl.gz`, via the bulk-data index at
 * `https://api.scryfall.com/bulk-data`) — this is NOT a guess. A real line looks like:
 * ```
 * {"object":"tag","id":"...","label":"removal","slug":"removal","type":"oracle",
 *  "uri":"...","description":"Get things off the table.","parent_ids":[],"child_ids":[],
 *  "aliases":[],"taggings":[{"oracle_id":"...","weight":"median"}, ...]}
 * ```
 * Only the fields this pipeline actually consumes are declared; everything else is dropped by
 * `ignoreUnknownKeys` (see [com.mmg.manahub.tools.tagpipeline.scryfall.ScryfallBulkClient]'s Json
 * instance).
 */
@Serializable
data class OracleTagDto(
    @SerialName("slug") val slug: String,
    /** `"oracle"` (functional tags, matched by `oracle_id`) vs `"illustration"` (art tags, matched
     *  by `illustration_id` — a different bulk file, `art_tags`, not consumed here). Defensively
     *  filtered even though the `oracle_tags` file is expected to contain only `"oracle"` rows. */
    @SerialName("type") val type: String,
    @SerialName("taggings") val taggings: List<TaggingDto> = emptyList(),
)

@Serializable
data class TaggingDto(
    @SerialName("oracle_id") val oracleId: String? = null,
)

/**
 * Folds a sequence of [OracleTagDto] rows into `oracle_id -> set of tag slugs`. Pure, no I/O —
 * unit-testable directly against fixture rows (see `TaggerTagMappingTest`/`RuleEngineParityTest`).
 */
fun buildOracleTagIndex(tagRows: Sequence<OracleTagDto>): Map<String, Set<String>> {
    val index = HashMap<String, MutableSet<String>>()
    tagRows
        .filter { it.type == "oracle" }
        .forEach { tag ->
            tag.taggings.forEach { tagging ->
                val oracleId = tagging.oracleId ?: return@forEach
                index.getOrPut(oracleId) { mutableSetOf() }.add(tag.slug)
            }
        }
    return index
}
