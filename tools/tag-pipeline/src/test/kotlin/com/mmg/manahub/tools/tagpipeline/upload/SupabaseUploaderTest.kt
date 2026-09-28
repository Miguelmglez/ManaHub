package com.mmg.manahub.tools.tagpipeline.upload

import com.mmg.manahub.tools.tagpipeline.model.buildCardStrategyTagsRow
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupabaseUploaderTest {

    private fun row(oracleId: String, generatedAt: String = "2026-07-21T00:00:00Z") = buildCardStrategyTagsRow(
        oracleId = oracleId,
        tags = setOf("ramp"),
        tribes = emptySet(),
        sources = setOf("rule_engine"),
        generatedAt = generatedAt,
        pipelineVersion = 1,
    )

    private fun fakeResponse(status: Int, body: String = "[]"): HttpResponse<String> =
        object : HttpResponse<String> {
            override fun statusCode() = status
            override fun request(): HttpRequest = throw UnsupportedOperationException()
            override fun previousResponse() = java.util.Optional.empty<HttpResponse<String>>()
            override fun headers(): java.net.http.HttpHeaders = java.net.http.HttpHeaders.of(emptyMap()) { _, _ -> true }
            override fun body(): String = body
            override fun sslSession() = java.util.Optional.empty<javax.net.ssl.SSLSession>()
            override fun uri(): java.net.URI = java.net.URI.create("https://example.test")
            override fun version() = java.net.http.HttpClient.Version.HTTP_1_1
        }

    @Test
    fun `dry run submits nothing over the network but still counts rows and batches`() {
        var callCount = 0
        val uploader = SupabaseUploader(
            SupabaseUploadConfig("https://x.test", "key", batchSize = 2),
            send = { callCount++; fakeResponse(200) },
        )
        val summary = uploader.uploadRows(sequenceOf(row("a"), row("b"), row("c")), dryRun = true)

        assertEquals(0, callCount)
        assertEquals(3, summary.rowsSubmitted)
        assertEquals(2, summary.batchCount) // batchSize=2 -> [a,b], [c]
        assertTrue(summary.dryRun)
        assertTrue(summary.allBatchesOk)
    }

    @Test
    fun `rows are chunked into batches of the configured size`() {
        val requests = mutableListOf<HttpRequest>()
        val uploader = SupabaseUploader(
            SupabaseUploadConfig("https://x.test", "key", batchSize = 2),
            send = { req -> requests.add(req); fakeResponse(200) },
        )
        uploader.uploadRows(sequenceOf(row("a"), row("b"), row("c"), row("d"), row("e")))

        assertEquals(3, requests.size) // [a,b], [c,d], [e]
    }

    @Test
    fun `each request targets card_strategy_tags with the merge-duplicates upsert header`() {
        var captured: HttpRequest? = null
        val uploader = SupabaseUploader(
            SupabaseUploadConfig("https://x.test", "svc-key"),
            send = { req -> captured = req; fakeResponse(200) },
        )
        uploader.uploadRows(sequenceOf(row("a")))

        val request = requireNotNull(captured)
        assertTrue(request.uri().toString().contains("/rest/v1/card_strategy_tags"))
        assertTrue(request.uri().toString().contains("on_conflict=oracle_id"))
        assertEquals(listOf("svc-key"), request.headers().allValues("apikey"))
        assertEquals(listOf("Bearer svc-key"), request.headers().allValues("Authorization"))
        assertEquals(listOf("resolution=merge-duplicates,return=minimal"), request.headers().allValues("Prefer"))
    }

    @Test
    fun `a failing batch is counted but does not abort the remaining batches`() {
        var call = 0
        val uploader = SupabaseUploader(
            SupabaseUploadConfig("https://x.test", "key", batchSize = 1),
            send = { call++; if (call == 1) fakeResponse(500, "server error") else fakeResponse(200) },
        )
        val summary = uploader.uploadRows(sequenceOf(row("a"), row("b"), row("c")))

        assertEquals(3, call)
        assertEquals(1, summary.failedBatches)
        assertFalse(summary.allBatchesOk)
    }

    @Test
    fun `a thrown exception on send is treated as a failed batch, not a crash`() {
        val uploader = SupabaseUploader(
            SupabaseUploadConfig("https://x.test", "key"),
            send = { throw java.io.IOException("connection refused") },
        )
        val summary = uploader.uploadRows(sequenceOf(row("a")))

        assertEquals(1, summary.failedBatches)
        assertFalse(summary.allBatchesOk)
    }

    @Test
    fun `accepted upsert with missing read-back row fails verification`() {
        val requests = mutableListOf<HttpRequest>()
        val uploader = SupabaseUploader(
            SupabaseUploadConfig("https://x.test", "key", verifyReadback = true),
            send = { request ->
                requests += request
                fakeResponse(if (request.method() == "POST") 201 else 200)
            },
        )

        val summary = uploader.uploadRows(sequenceOf(row("a")))

        assertEquals(listOf("POST", "GET"), requests.map { it.method() })
        assertEquals(1, summary.failedBatches)
        assertFalse(summary.allBatchesOk)
    }

    @Test
    fun `read-back accepts equivalent UTC timestamp representations`() {
        val uploader = SupabaseUploader(
            SupabaseUploadConfig("https://x.test", "key", verifyReadback = true),
            send = { request ->
                if (request.method() == "POST") fakeResponse(201)
                else fakeResponse(200, readbackRow("2026-09-28 09:38:04.999041+00"))
            },
        )

        val summary = uploader.uploadRows(sequenceOf(row("a", "2026-09-28T09:38:04.999041Z")))

        assertTrue(summary.allBatchesOk)
    }

    @Test
    fun `read-back rejects changed payload even when timestamps represent the same instant`() {
        val uploader = SupabaseUploader(
            SupabaseUploadConfig("https://x.test", "key", verifyReadback = true),
            send = { request ->
                if (request.method() == "POST") fakeResponse(201)
                else fakeResponse(200, readbackRow("2026-09-28T09:38:04.999041+00:00", tag = "draw"))
            },
        )

        val summary = uploader.uploadRows(sequenceOf(row("a", "2026-09-28T09:38:04.999041Z")))

        assertEquals(1, summary.failedBatches)
    }

    private fun readbackRow(generatedAt: String, tag: String = "ramp") =
        """[{"oracle_id":"a","payload":{"tags":["$tag"],"suggestions":[],"input_fingerprint":"","tribes":[],"themes":{},"archetypes":{},"sources":["rule_engine"],"archidekt_category":null},"pipeline_version":"1","generated_at":"$generatedAt"}]"""

    @Test
    fun `startPipelineRun parses the returned id from a representation response`() {
        val uploader = SupabaseUploader(
            SupabaseUploadConfig("https://x.test", "key"),
            send = { fakeResponse(201, """[{"id":"run-123","status":"running"}]""") },
        )
        assertEquals("run-123", uploader.startPipelineRun("1"))
    }

    @Test
    fun `startPipelineRun degrades to null on failure, never throws`() {
        val uploader = SupabaseUploader(
            SupabaseUploadConfig("https://x.test", "key"),
            send = { throw java.io.IOException("down") },
        )
        assertEquals(null, uploader.startPipelineRun("1"))
    }

    @Test
    fun `completePipelineRun is a no-op when runId is null`() {
        var called = false
        val uploader = SupabaseUploader(
            SupabaseUploadConfig("https://x.test", "key"),
            send = { called = true; fakeResponse(200) },
        )
        uploader.completePipelineRun(null, cardsProcessed = 10, status = "completed")
        assertFalse(called)
    }

    @Test
    fun `completePipelineRun sends a PATCH to the specific run id, never throws on failure`() {
        var captured: HttpRequest? = null
        val uploader = SupabaseUploader(
            SupabaseUploadConfig("https://x.test", "key"),
            send = { req -> captured = req; fakeResponse(500) },
        )
        uploader.completePipelineRun("run-123", cardsProcessed = 5, status = "failed")

        val request = requireNotNull(captured)
        assertEquals("PATCH", request.method())
        assertTrue(request.uri().toString().contains("pipeline_runs?id=eq.run-123"))
    }
}
