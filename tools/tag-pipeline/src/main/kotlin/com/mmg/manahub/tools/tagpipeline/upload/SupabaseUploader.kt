package com.mmg.manahub.tools.tagpipeline.upload

import com.mmg.manahub.tools.tagpipeline.io.PIPELINE_JSON
import com.mmg.manahub.tools.tagpipeline.model.CardStrategyTagsRow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Writes the pipeline's JSONL output to the live Supabase `card_strategy_tags` table (plan §5 Phase
 * 5b's already-decided write path — "Direct service-role write, no RPC", see
 * `.claude/agent-memory/backend-supabase-expert/project_card_strategy_tags_pipeline.md`) and keeps
 * `pipeline_runs` bookkeeping ([startPipelineRun]/[completePipelineRun]).
 *
 * Deliberately plain `java.net.http.HttpClient` (matches [com.mmg.manahub.tools.tagpipeline.edhrec
 * .EdhrecThemeClient]/`ScryfallBulkClient`'s established in-module convention) rather than Ktor — a
 * handful of batched JSON POSTs needs none of Ktor's plugin machinery, and this keeps the Ktor
 * dependency this module DOES carry (see `build.gradle.kts`) scoped to its one real justification
 * (literal [com.mmg.manahub.core.data.remote.ArchidektClient] reuse).
 *
 * [send] is injectable so [SupabaseUploaderTest] can assert exact request shape (URL, headers,
 * batching, body) against a fake without a real network call or a mocked HTTP server.
 */
class SupabaseUploader(
    private val config: SupabaseUploadConfig,
    private val send: (HttpRequest) -> HttpResponse<String> = { request ->
        DEFAULT_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
    },
) {

    /**
     * Batches [rows] into chunks of [SupabaseUploadConfig.batchSize] and upserts each chunk via
     * `POST .../card_strategy_tags?on_conflict=oracle_id` with `Prefer: resolution=merge-duplicates`
     * (PK is `oracle_id`, so a re-run of the same card is a plain overwrite, never a duplicate row).
     * A single batch failure is logged and counted, NOT fatal — the run continues with the remaining
     * batches (mirrors this module's "one bad unit never aborts the whole run" posture, same as the
     * EDHREC/Archidekt fetchers). [dryRun] validates batching/shape without any network call at all.
     */
    fun uploadRows(rows: Sequence<CardStrategyTagsRow>, dryRun: Boolean = false): UploadSummary {
        var rowsSubmitted = 0L
        var batchCount = 0
        var failedBatches = 0

        rows.chunked(config.batchSize.coerceAtLeast(1)).forEach { batch ->
            batchCount++
            rowsSubmitted += batch.size
            if (dryRun) return@forEach

            val body = PIPELINE_JSON.encodeToString(
                ListSerializer(UploadRow.serializer()),
                batch.map { it.toUploadRow() },
            )
            val request = HttpRequest.newBuilder(
                URI.create("${config.supabaseUrl}/rest/v1/card_strategy_tags?on_conflict=oracle_id"),
            )
                .header("apikey", config.serviceRoleKey)
                .header("Authorization", "Bearer ${config.serviceRoleKey}")
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

            val ok = runCatchingBatch(batchCount, batch.size) { send(request) }
            if (!ok) failedBatches++
        }

        return UploadSummary(rowsSubmitted = rowsSubmitted, batchCount = batchCount, failedBatches = failedBatches, dryRun = dryRun)
    }

    /** Inserts a `status='running'` row and returns its `id` (used by [completePipelineRun]), or
     *  `null` on any failure — bookkeeping is best-effort, it must never block the actual data
     *  upload above. */
    fun startPipelineRun(pipelineVersion: String): String? {
        val body = PIPELINE_JSON.encodeToString(
            PipelineRunStart.serializer(),
            PipelineRunStart(pipelineVersion = pipelineVersion, status = "running"),
        )
        val request = HttpRequest.newBuilder(URI.create("${config.supabaseUrl}/rest/v1/pipeline_runs"))
            .header("apikey", config.serviceRoleKey)
            .header("Authorization", "Bearer ${config.serviceRoleKey}")
            .header("Content-Type", "application/json")
            .header("Prefer", "return=representation")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return try {
            val response = send(request)
            if (response.statusCode() !in 200..299) {
                System.err.println("[tag-pipeline] pipeline_runs insert failed: HTTP ${response.statusCode()}")
                return null
            }
            val parsed = LENIENT_JSON.parseToJsonElement(response.body())
            (parsed as? JsonArray)?.firstOrNull()
                ?.let { it as? JsonObject }
                ?.get("id")?.jsonPrimitive?.content
        } catch (e: Exception) {
            System.err.println("[tag-pipeline] pipeline_runs insert failed: ${e.message}")
            null
        }
    }

    /** Updates the [runId] row (from [startPipelineRun]) with completion status. No-op (logged, not
     *  thrown) when [runId] is null — the run's actual data was already uploaded either way. */
    fun completePipelineRun(runId: String?, cardsProcessed: Long, status: String) {
        if (runId == null) return
        val body = PIPELINE_JSON.encodeToString(
            PipelineRunComplete.serializer(),
            PipelineRunComplete(completedAt = java.time.Instant.now().toString(), cardsProcessed = cardsProcessed, status = status),
        )
        val request = HttpRequest.newBuilder(URI.create("${config.supabaseUrl}/rest/v1/pipeline_runs?id=eq.$runId"))
            .header("apikey", config.serviceRoleKey)
            .header("Authorization", "Bearer ${config.serviceRoleKey}")
            .header("Content-Type", "application/json")
            .header("Prefer", "return=minimal")
            .timeout(Duration.ofSeconds(30))
            .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
            .build()
        try {
            val response = send(request)
            if (response.statusCode() !in 200..299) {
                System.err.println("[tag-pipeline] pipeline_runs update failed: HTTP ${response.statusCode()}")
            }
        } catch (e: Exception) {
            System.err.println("[tag-pipeline] pipeline_runs update failed: ${e.message}")
        }
    }

    private inline fun runCatchingBatch(batchNumber: Int, size: Int, block: () -> HttpResponse<String>): Boolean = try {
        val response = block()
        if (response.statusCode() in 200..299) {
            System.err.println("[tag-pipeline] batch $batchNumber ($size rows) uploaded (HTTP ${response.statusCode()})")
            true
        } else {
            System.err.println(
                "[tag-pipeline] batch $batchNumber ($size rows) FAILED: HTTP ${response.statusCode()} " +
                    "- ${response.body().take(300)}",
            )
            false
        }
    } catch (e: Exception) {
        System.err.println("[tag-pipeline] batch $batchNumber ($size rows) FAILED: ${e.message}")
        false
    }

    companion object {
        private val DEFAULT_HTTP_CLIENT: HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        private val LENIENT_JSON = Json { ignoreUnknownKeys = true }
    }
}

data class SupabaseUploadConfig(
    val supabaseUrl: String,
    val serviceRoleKey: String,
    val batchSize: Int = 1000,
)

data class UploadSummary(
    val rowsSubmitted: Long,
    val batchCount: Int,
    val failedBatches: Int,
    val dryRun: Boolean,
) {
    val allBatchesOk: Boolean get() = failedBatches == 0
}

/** Maps [CardStrategyTagsRow]'s field-per-JSONL-row shape onto the table's dedicated-columns +
 *  generic-payload-JSONB shape (`oracle_id`/`pipeline_version`/`generated_at` are real columns;
 *  everything else lives in `payload jsonb` — see `project_card_strategy_tags_pipeline` memory). */
@Serializable
private data class UploadRow(
    @SerialName("oracle_id") val oracleId: String,
    val payload: UploadPayload,
    @SerialName("pipeline_version") val pipelineVersion: String,
    @SerialName("generated_at") val generatedAt: String,
)

@Serializable
private data class UploadPayload(
    val tags: List<String>,
    val tribes: List<String>,
    val themes: Map<String, Float>,
    val archetypes: Map<String, Float>,
    val sources: List<String>,
    @SerialName("archidekt_category") val archidektCategory: String? = null,
)

private fun CardStrategyTagsRow.toUploadRow(): UploadRow = UploadRow(
    oracleId = oracleId,
    payload = UploadPayload(
        tags = tags,
        tribes = tribes,
        themes = themes,
        archetypes = archetypes,
        sources = sources,
        archidektCategory = archidektCategory,
    ),
    // The `pipeline_version` COLUMN is `text` (see the migration) even though the JSONL field is an
    // Int — converted here, once, at the upload boundary, so the rest of the pipeline never has to
    // care about the column's SQL type.
    pipelineVersion = pipelineVersion.toString(),
    generatedAt = generatedAt,
)

@Serializable
private data class PipelineRunStart(
    @SerialName("pipeline_version") val pipelineVersion: String,
    val status: String,
)

@Serializable
private data class PipelineRunComplete(
    @SerialName("completed_at") val completedAt: String,
    @SerialName("cards_processed") val cardsProcessed: Long,
    val status: String,
)
