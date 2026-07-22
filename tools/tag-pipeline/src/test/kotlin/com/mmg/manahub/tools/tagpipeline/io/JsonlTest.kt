package com.mmg.manahub.tools.tagpipeline.io

import com.mmg.manahub.tools.tagpipeline.model.buildCardStrategyTagsRow
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonlTest {

    @Test
    fun `buildCardStrategyTagsRow sorts tags, tribes, sources, and map keys`() {
        val row = buildCardStrategyTagsRow(
            oracleId = "o1",
            tags = setOf("tokens", "aggro", "midrange"),
            tribes = setOf("zombie", "elf"),
            themes = mapOf("VOLTRON" to 0.1f, "ARISTOCRATS" to 0.2f),
            sources = setOf("edhrec", "rule_engine", "oracle_tags"),
            generatedAt = "2026-01-01T00:00:00Z",
            pipelineVersion = 1,
        )
        assertEquals(listOf("aggro", "midrange", "tokens"), row.tags)
        assertEquals(listOf("elf", "zombie"), row.tribes)
        assertEquals(listOf("edhrec", "oracle_tags", "rule_engine"), row.sources)
        assertEquals(listOf("ARISTOCRATS", "VOLTRON"), row.themes.keys.toList())
    }

    @Test
    fun `writeJsonl then readJsonl round-trips rows exactly`() {
        val dir = Files.createTempDirectory("jsonl-test")
        val path = dir.resolve("rows.jsonl")
        val rows = listOf(
            buildCardStrategyTagsRow(
                oracleId = "o1", tags = setOf("removal"), tribes = emptySet(),
                sources = setOf("rule_engine"), generatedAt = "2026-01-01T00:00:00Z", pipelineVersion = 1,
            ),
            buildCardStrategyTagsRow(
                oracleId = "o2", tags = setOf("ramp", "tokens"), tribes = setOf("goblin"),
                themes = mapOf("TOKENS" to 0.5f), sources = setOf("rule_engine", "edhrec"),
                generatedAt = "2026-01-01T00:00:00Z", pipelineVersion = 1,
            ),
        )
        val written = writeJsonl(path, com.mmg.manahub.tools.tagpipeline.model.CardStrategyTagsRow.serializer(), rows.asSequence())
        assertEquals(2L, written)

        val readBack = readJsonl(path, com.mmg.manahub.tools.tagpipeline.model.CardStrategyTagsRow.serializer(), gzip = false).toList()
        assertEquals(rows, readBack)
    }

    @Test
    fun `writeJsonl always emits every field, even ones equal to their default value`() {
        // Regression test: found via the real smoke-test run (RUN 5) — without `encodeDefaults =
        // true` on PIPELINE_JSON, a row whose themes/archetypes are the default emptyMap() had
        // those KEYS OMITTED from the JSON entirely, not just emitted as `{}`. Every row must have
        // an identical, predictable key set for the downstream Supabase JSONB payload.
        val dir = Files.createTempDirectory("jsonl-test")
        val path = dir.resolve("defaults.jsonl")
        val row = buildCardStrategyTagsRow(
            oracleId = "o1", tags = emptySet(), tribes = emptySet(),
            sources = emptySet(), generatedAt = "x", pipelineVersion = 1,
            // themes/archetypes intentionally left at their default emptyMap().
        )
        writeJsonl(path, com.mmg.manahub.tools.tagpipeline.model.CardStrategyTagsRow.serializer(), sequenceOf(row))
        val rawLine = Files.readAllLines(path).single()
        assertTrue("\"themes\"" in rawLine, "expected the themes key to be present even when empty: $rawLine")
        assertTrue("\"archetypes\"" in rawLine, "expected the archetypes key to be present even when empty: $rawLine")
    }

    @Test
    fun `writeJsonl of an empty sequence writes an empty file and returns 0`() {
        val dir = Files.createTempDirectory("jsonl-test")
        val path = dir.resolve("empty.jsonl")
        val written = writeJsonl(path, com.mmg.manahub.tools.tagpipeline.model.CardStrategyTagsRow.serializer(), emptySequence())
        assertEquals(0L, written)
        assertTrue(Files.exists(path))
        assertEquals("", Files.readString(path))
    }

    @Test
    fun `readJsonl decodes a gzip-compressed file`() {
        val dir = Files.createTempDirectory("jsonl-test")
        val path = dir.resolve("rows.jsonl.gz")
        val json = """{"oracleId":"o1","tags":["removal"],"tribes":[],"themes":{},"archetypes":{},"sources":["rule_engine"],"generatedAt":"2026-01-01T00:00:00Z","pipelineVersion":1}"""
        GZIPOutputStream(Files.newOutputStream(path)).use { it.write(json.toByteArray()) }

        val rows = readJsonl(path, com.mmg.manahub.tools.tagpipeline.model.CardStrategyTagsRow.serializer(), gzip = true).toList()
        assertEquals(1, rows.size)
        assertEquals("o1", rows.single().oracleId)
    }

    @Test
    fun `writeJsonl creates the output's parent directories when they don't exist yet`() {
        // Regression test: found via the real smoke-test run (RUN 5) — `--out build/pipeline-out/
        // smoke.jsonl` failed with NoSuchFileException because Files.createTempFile requires the
        // parent directory to already exist and nothing created it first.
        val dir = Files.createTempDirectory("jsonl-test")
        val nested = dir.resolve("a/b/c/rows.jsonl")
        val rows = listOf(
            buildCardStrategyTagsRow(
                oracleId = "o1", tags = emptySet(), tribes = emptySet(),
                sources = emptySet(), generatedAt = "x", pipelineVersion = 1,
            ),
        )
        val written = writeJsonl(nested, com.mmg.manahub.tools.tagpipeline.model.CardStrategyTagsRow.serializer(), rows.asSequence())
        assertEquals(1L, written)
        assertTrue(Files.exists(nested))
    }

    @Test
    fun `readJsonl skips a malformed line rather than aborting the whole stream`() {
        val dir = Files.createTempDirectory("jsonl-test")
        val path = dir.resolve("mixed.jsonl")
        val goodLine = """{"oracleId":"o1","tags":[],"tribes":[],"themes":{},"archetypes":{},"sources":[],"generatedAt":"x","pipelineVersion":1}"""
        Files.writeString(path, "$goodLine\nnot valid json\n$goodLine\n")

        val rows = readJsonl(path, com.mmg.manahub.tools.tagpipeline.model.CardStrategyTagsRow.serializer(), gzip = false).toList()
        assertEquals(2, rows.size)
    }
}
