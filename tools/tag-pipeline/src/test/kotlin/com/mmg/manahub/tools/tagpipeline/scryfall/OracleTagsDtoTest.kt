package com.mmg.manahub.tools.tagpipeline.scryfall

import com.mmg.manahub.tools.tagpipeline.io.PIPELINE_JSON
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OracleTagsDtoTest {

    /** A REAL line captured from a live download of Scryfall's `oracle_tags` bulk file
     *  (`data.scryfall.io/oracle-tags/...jsonl.gz`, 2026-07-20) — not synthesized. Proves
     *  [OracleTagDto] decodes the actual wire format, not a guessed shape. */
    private val realGravePactLine = """
        {"object":"tag","id":"0025ee30-ee96-4c59-8561-74988efd384c","label":"grave pact",
        "slug":"grave-pact","type":"oracle","uri":"https://tagger.scryfall.com/tags/card/grave-pact",
        "description":"Cards that have abilities worded: \"whenever creature you contol dies, each opponent sacrifices a creature.\"",
        "parent_ids":[],"child_ids":[],"aliases":[],
        "taggings":[{"oracle_id":"a85197ab-dc94-4b72-9716-8dbdbbe90ff8","weight":"median"},
        {"oracle_id":"7c777a41-e40a-4b40-96bf-8ddd5c12924c","weight":"median"},
        {"oracle_id":"6f4ac4a4-53ec-4bc9-8f5c-d4b801d867b2","weight":"very_strong"}]}
    """.trimIndent().replace("\n", "")

    @Test
    fun `decodes a real captured oracle_tags line`() {
        val dto = PIPELINE_JSON.decodeFromString(OracleTagDto.serializer(), realGravePactLine)
        assertEquals("grave-pact", dto.slug)
        assertEquals("oracle", dto.type)
        assertEquals(3, dto.taggings.size)
        assertEquals("a85197ab-dc94-4b72-9716-8dbdbbe90ff8", dto.taggings[0].oracleId)
    }

    @Test
    fun `unknown fields are ignored (ignoreUnknownKeys)`() {
        // Same real line, plus a field this pipeline doesn't declare — must not throw.
        val withExtraField = realGravePactLine.dropLast(1) + ""","totally_new_upstream_field":"whatever"}"""
        val dto = PIPELINE_JSON.decodeFromString(OracleTagDto.serializer(), withExtraField)
        assertEquals("grave-pact", dto.slug)
    }

    @Test
    fun `buildOracleTagIndex folds taggings into an oracle_id to slug-set map`() {
        val dto = PIPELINE_JSON.decodeFromString(OracleTagDto.serializer(), realGravePactLine)
        val index = buildOracleTagIndex(sequenceOf(dto))
        assertEquals(setOf("grave-pact"), index["a85197ab-dc94-4b72-9716-8dbdbbe90ff8"])
        assertEquals(setOf("grave-pact"), index["6f4ac4a4-53ec-4bc9-8f5c-d4b801d867b2"])
        assertTrue("some-other-oracle-id" !in index)
    }

    @Test
    fun `buildOracleTagIndex merges multiple tags for the same card`() {
        val tagA = OracleTagDto(slug = "removal", type = "oracle", taggings = listOf(TaggingDto("card-1")))
        val tagB = OracleTagDto(slug = "sweeper", type = "oracle", taggings = listOf(TaggingDto("card-1")))
        val index = buildOracleTagIndex(sequenceOf(tagA, tagB))
        assertEquals(setOf("removal", "sweeper"), index["card-1"])
    }

    @Test
    fun `buildOracleTagIndex filters non-oracle-type rows (art tags)`() {
        // A synthetic (not captured) row shaped like an illustration/art tag, to prove the type
        // filter — this pipeline never downloads the separate art_tags bulk file, but a defensive
        // filter costs nothing and documents the assumption.
        val artTag = OracleTagDto(slug = "some-art-tag", type = "illustration", taggings = listOf(TaggingDto("card-1")))
        val index = buildOracleTagIndex(sequenceOf(artTag))
        assertTrue(index.isEmpty())
    }

    @Test
    fun `buildOracleTagIndex skips a tagging with a null oracle_id`() {
        val tag = OracleTagDto(slug = "removal", type = "oracle", taggings = listOf(TaggingDto(oracleId = null)))
        val index = buildOracleTagIndex(sequenceOf(tag))
        assertTrue(index.isEmpty())
    }
}
