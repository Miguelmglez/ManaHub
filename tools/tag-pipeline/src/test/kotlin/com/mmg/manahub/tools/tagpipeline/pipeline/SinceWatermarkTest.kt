package com.mmg.manahub.tools.tagpipeline.pipeline

import com.mmg.manahub.tools.tagpipeline.io.writeJsonl
import com.mmg.manahub.tools.tagpipeline.model.buildCardStrategyTagsRow
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SinceWatermarkTest {

    @Test
    fun `card absent from manifest should be reprocessed`() {
        assertTrue(SinceWatermark.shouldReprocess("new-card", currentPipelineVersion = 1, manifest = emptyMap()))
    }

    @Test
    fun `card at an older pipeline_version should be reprocessed`() {
        val manifest = mapOf("card-a" to 1)
        assertTrue(SinceWatermark.shouldReprocess("card-a", currentPipelineVersion = 2, manifest = manifest))
    }

    @Test
    fun `card at the current pipeline_version should NOT be reprocessed`() {
        val manifest = mapOf("card-a" to 2)
        assertFalse(SinceWatermark.shouldReprocess("card-a", currentPipelineVersion = 2, manifest = manifest))
    }

    @Test
    fun `card at a NEWER pipeline_version than current should not be reprocessed`() {
        // Defensive: shouldn't normally happen (versions only go up), but must not loop/crash.
        val manifest = mapOf("card-a" to 5)
        assertFalse(SinceWatermark.shouldReprocess("card-a", currentPipelineVersion = 2, manifest = manifest))
    }

    @Test
    fun `loadManifest builds oracle_id to pipeline_version from a previous run's JSONL`() {
        val dir = Files.createTempDirectory("since-watermark-test")
        val previousOutput = dir.resolve("previous.jsonl")
        val rows = sequenceOf(
            buildCardStrategyTagsRow(
                oracleId = "oracle-1", tags = setOf("removal"), tribes = emptySet(),
                sources = setOf("rule_engine"), generatedAt = "2026-01-01T00:00:00Z", pipelineVersion = 1,
            ),
            buildCardStrategyTagsRow(
                oracleId = "oracle-2", tags = emptySet(), tribes = emptySet(),
                sources = setOf("rule_engine"), generatedAt = "2026-01-01T00:00:00Z", pipelineVersion = 3,
            ),
        )
        writeJsonl(previousOutput, com.mmg.manahub.tools.tagpipeline.model.CardStrategyTagsRow.serializer(), rows)

        val manifest = SinceWatermark.loadManifest(previousOutput)
        assertEquals(mapOf("oracle-1" to 1, "oracle-2" to 3), manifest)
    }

    @Test
    fun `loadManifest of a missing file degrades to empty map, never throws`() {
        val missing = Files.createTempDirectory("since-watermark-test").resolve("does-not-exist.jsonl")
        assertEquals(emptyMap(), SinceWatermark.loadManifest(missing))
    }

    @Test
    fun `loadManifest of a corrupt file degrades to empty map, never throws`() {
        val dir = Files.createTempDirectory("since-watermark-test")
        val corrupt = dir.resolve("corrupt.jsonl")
        Files.writeString(corrupt, "{ this is not valid json at all")
        assertEquals(emptyMap(), SinceWatermark.loadManifest(corrupt))
    }
}
