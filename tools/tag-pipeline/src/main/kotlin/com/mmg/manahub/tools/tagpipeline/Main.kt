package com.mmg.manahub.tools.tagpipeline

import com.mmg.manahub.core.data.network.ArchidektRequestQueue
import com.mmg.manahub.core.data.remote.ArchidektClient
import com.mmg.manahub.tools.tagpipeline.archidekt.ArchidektCategorySampler
import com.mmg.manahub.tools.tagpipeline.archidekt.resolveDominantCategories
import com.mmg.manahub.tools.tagpipeline.edhrec.EdhrecThemeClient
import com.mmg.manahub.tools.tagpipeline.io.DiskCache
import com.mmg.manahub.tools.tagpipeline.io.PIPELINE_JSON
import com.mmg.manahub.tools.tagpipeline.io.readJsonl
import com.mmg.manahub.tools.tagpipeline.io.writeJsonl
import com.mmg.manahub.tools.tagpipeline.model.CardStrategyTagsRow
import com.mmg.manahub.tools.tagpipeline.pipeline.PIPELINE_VERSION
import com.mmg.manahub.tools.tagpipeline.pipeline.SinceFilter
import com.mmg.manahub.tools.tagpipeline.pipeline.SinceWatermark
import com.mmg.manahub.tools.tagpipeline.pipeline.TagPipeline
import com.mmg.manahub.tools.tagpipeline.scryfall.ScryfallBulkClient
import com.mmg.manahub.tools.tagpipeline.upload.SupabaseCredentials
import com.mmg.manahub.tools.tagpipeline.upload.SupabaseUploadConfig
import com.mmg.manahub.tools.tagpipeline.upload.SupabaseUploader
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime

/**
 * CLI entry point (Deck Engine Unification plan, RUN 5 / D5; extended by the "Pipeline production-
 * readiness" run for Archidekt enrichment + the Supabase upload subcommand).
 *
 * See `README.md` in this module for the full command reference. Quick reference:
 * ```
 * # Generate tags
 * ./gradlew :tools:tag-pipeline:run --args="run --out build/pipeline-out/rows.jsonl --limit 200"
 *
 * # Upload a previously generated file to Supabase (needs SUPABASE_SERVICE_ROLE_KEY)
 * ./gradlew :tools:tag-pipeline:run --args="upload --in build/pipeline-out/rows.jsonl"
 * ```
 *
 * The first argument selects the subcommand (`run` if omitted, for backward compatibility with the
 * original RUN 5 invocation shape that had no subcommand at all).
 */
@OptIn(ExperimentalTime::class)
fun main(args: Array<String>) {
    val (subcommand, rest) = when (args.firstOrNull()) {
        "run" -> "run" to args.drop(1).toTypedArray()
        "upload" -> "upload" to args.drop(1).toTypedArray()
        else -> "run" to args // backward-compatible default: no subcommand = pipeline run
    }

    when (subcommand) {
        "run" -> runPipeline(rest)
        "upload" -> runUpload(rest)
    }
}

// ── `run` subcommand: generate the JSONL tag rows ──────────────────────────────────────────────

@OptIn(ExperimentalTime::class)
private fun runPipeline(args: Array<String>) {
    val opts = try {
        RunOptions.parse(args)
    } catch (e: IllegalArgumentException) {
        System.err.println("[tag-pipeline] ${e.message}")
        System.err.println(RUN_USAGE)
        exitProcess(1)
    }

    val cache = DiskCache(opts.cacheDir)
    val bulkClient = ScryfallBulkClient(cache)
    val edhrecClient = EdhrecThemeClient(cache)
    val pipeline = TagPipeline()

    val generatedAt = Clock.System.now().toString()

    System.err.println("[tag-pipeline] fetching oracle_cards bulk file (cached at ${opts.cacheDir})...")
    var cardSequence = bulkClient.streamOracleCards()
    if (opts.limit != null) {
        System.err.println("[tag-pipeline] --limit ${opts.limit}: smoke-test slice, not a full run")
        cardSequence = cardSequence.take(opts.limit)
    }

    val oracleTagRows = if (opts.skipOracleTags) {
        System.err.println("[tag-pipeline] --skip-oracle-tags: rule-engine + EDHREC tags only")
        emptySequence()
    } else {
        System.err.println("[tag-pipeline] fetching oracle_tags bulk file...")
        bulkClient.streamOracleTagsOrEmpty()
    }

    val themeIndex = if (opts.skipEdhrec) {
        System.err.println("[tag-pipeline] --skip-edhrec: no theme/archetype affinities this run")
        emptyMap()
    } else {
        System.err.println("[tag-pipeline] fetching EDHREC theme pages...")
        pipeline.buildThemeIndexByCardName(edhrecClient)
    }

    val archetypeIndex = if (opts.skipEdhrec) {
        emptyMap()
    } else {
        System.err.println("[tag-pipeline] fetching EDHREC archetype pages...")
        pipeline.buildArchetypeIndexByCardName(edhrecClient)
    }

    val archidektCategoryByOracleId = if (opts.archidektDecks > 0) {
        System.err.println("[tag-pipeline] sampling ${opts.archidektDecks} Archidekt deck(s) for category enrichment...")
        sampleArchidektCategories(opts.archidektDecks, opts.archidektMinObservations, opts.archidektMinShare)
    } else {
        emptyMap()
    }

    val sinceFilter = opts.since?.let { path ->
        System.err.println("[tag-pipeline] --since $path: loading watermark manifest...")
        SinceFilter(SinceWatermark.loadManifest(path))
    }

    val rows = pipeline.buildRows(
        cardDtos = cardSequence,
        oracleTagRows = oracleTagRows,
        themeIndexByCardName = themeIndex,
        generatedAt = generatedAt,
        pipelineVersion = PIPELINE_VERSION,
        since = sinceFilter,
        archidektCategoryByOracleId = archidektCategoryByOracleId,
        archetypeIndexByCardName = archetypeIndex,
    )

    val count = writeJsonl(opts.out, CardStrategyTagsRow.serializer(), rows)
    System.err.println("[tag-pipeline] wrote $count row(s) to ${opts.out}")
}

/** Builds the Ktor `HttpClient` + [ArchidektClient] and runs [ArchidektCategorySampler] to
 *  completion. Any failure anywhere in the sampling pass degrades to "no Archidekt enrichment this
 *  run" (returns an empty map) rather than aborting the whole pipeline run — same fallibility
 *  posture as the EDHREC harvest. */
private fun sampleArchidektCategories(
    deckCount: Int,
    minObservations: Int,
    minShare: Float,
): Map<String, String> = try {
    val httpClient = HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) { json(PIPELINE_JSON) }
        defaultRequest {
            header("User-Agent", "Mozilla/5.0 (compatible; ManaHubTagPipeline/1; +https://github.com/Miguelmglez/ManaHub)")
            header("Accept", "application/json")
        }
    }
    try {
        val client = ArchidektClient(httpClient, baseUrl = "https://archidekt.com/")
        val sampler = ArchidektCategorySampler(client, ArchidektRequestQueue())
        runBlocking {
            val deckIds = sampler.collectDeckIds(deckCount)
            System.err.println("[tag-pipeline] Archidekt: sampling ${deckIds.size} deck(s)...")
            val observations = sampler.harvestCategoryObservations(deckIds)
            resolveDominantCategories(observations, minObservations, minShare)
        }
    } finally {
        httpClient.close()
    }
} catch (e: Exception) {
    System.err.println("[tag-pipeline] Archidekt enrichment failed, continuing without it: ${e.message}")
    emptyMap()
}

private class RunOptions(
    val out: Path,
    val cacheDir: Path,
    val limit: Int?,
    val since: Path?,
    val skipEdhrec: Boolean,
    val skipOracleTags: Boolean,
    val archidektDecks: Int,
    val archidektMinObservations: Int,
    val archidektMinShare: Float,
) {
    companion object {
        fun parse(args: Array<String>): RunOptions {
            var out: Path? = null
            var cacheDir: Path = Path.of("tools/tag-pipeline/.cache")
            var limit: Int? = null
            var since: Path? = null
            var skipEdhrec = false
            var skipOracleTags = false
            var archidektDecks = 0
            var archidektMinObservations = 3
            var archidektMinShare = 0.5f

            var i = 0
            while (i < args.size) {
                when (val arg = args[i]) {
                    "--out" -> { out = Path.of(requireValue(args, i, arg)); i++ }
                    "--cache-dir" -> { cacheDir = Path.of(requireValue(args, i, arg)); i++ }
                    "--limit" -> {
                        limit = requireValue(args, i, arg).toIntOrNull()
                            ?: throw IllegalArgumentException("--limit must be an integer")
                        i++
                    }
                    "--since" -> { since = Path.of(requireValue(args, i, arg)); i++ }
                    "--skip-edhrec" -> skipEdhrec = true
                    "--skip-oracle-tags" -> skipOracleTags = true
                    "--archidekt-decks" -> {
                        archidektDecks = requireValue(args, i, arg).toIntOrNull()
                            ?: throw IllegalArgumentException("--archidekt-decks must be an integer")
                        i++
                    }
                    "--archidekt-min-observations" -> {
                        archidektMinObservations = requireValue(args, i, arg).toIntOrNull()
                            ?: throw IllegalArgumentException("--archidekt-min-observations must be an integer")
                        i++
                    }
                    "--archidekt-min-share" -> {
                        archidektMinShare = requireValue(args, i, arg).toFloatOrNull()
                            ?: throw IllegalArgumentException("--archidekt-min-share must be a float")
                        i++
                    }
                    else -> throw IllegalArgumentException("Unknown argument: $arg")
                }
                i++
            }

            return RunOptions(
                out = out ?: throw IllegalArgumentException("--out <path> is required"),
                cacheDir = cacheDir,
                limit = limit,
                since = since,
                skipEdhrec = skipEdhrec,
                skipOracleTags = skipOracleTags,
                archidektDecks = archidektDecks,
                archidektMinObservations = archidektMinObservations,
                archidektMinShare = archidektMinShare,
            )
        }
    }
}

private const val RUN_USAGE = """
Usage: tag-pipeline [run] --out <path> [--cache-dir <path>] [--limit <n>] [--since <path>]
                     [--skip-edhrec] [--skip-oracle-tags]
                     [--archidekt-decks <n>] [--archidekt-min-observations <n>] [--archidekt-min-share <0..1>]
"""

// ── `upload` subcommand: push a JSONL file to Supabase `card_strategy_tags` ────────────────────

private fun runUpload(args: Array<String>) {
    val opts = try {
        UploadOptions.parse(args)
    } catch (e: IllegalArgumentException) {
        System.err.println("[tag-pipeline] ${e.message}")
        System.err.println(UPLOAD_USAGE)
        exitProcess(1)
    }

    val supabaseUrl = try {
        SupabaseCredentials.resolveUrl(opts.supabaseUrl)
    } catch (e: IllegalStateException) {
        System.err.println("[tag-pipeline] ${e.message}")
        exitProcess(1)
    }

    if (opts.dryRun) {
        System.err.println("[tag-pipeline] --dry-run: validating batching only, no network calls, no credentials required")
        val uploader = SupabaseUploader(SupabaseUploadConfig(supabaseUrl = supabaseUrl, serviceRoleKey = "dry-run", batchSize = opts.batchSize))
        val summary = uploader.uploadRows(readJsonl(opts.input, CardStrategyTagsRow.serializer(), gzip = false), dryRun = true)
        System.err.println("[tag-pipeline] dry-run: ${summary.rowsSubmitted} row(s) across ${summary.batchCount} batch(es) would be uploaded")
        return
    }

    val serviceRoleKey = try {
        SupabaseCredentials.resolveServiceRoleKey()
    } catch (e: IllegalStateException) {
        System.err.println("[tag-pipeline] ${e.message}")
        exitProcess(1)
    }

    val uploader = SupabaseUploader(SupabaseUploadConfig(supabaseUrl = supabaseUrl, serviceRoleKey = serviceRoleKey, batchSize = opts.batchSize))

    val runId = uploader.startPipelineRun(pipelineVersion = PIPELINE_VERSION.toString())
    System.err.println("[tag-pipeline] pipeline_runs row started" + (runId?.let { " (id=$it)" } ?: " (bookkeeping unavailable, continuing)"))

    val summary = uploader.uploadRows(readJsonl(opts.input, CardStrategyTagsRow.serializer(), gzip = false))
    System.err.println(
        "[tag-pipeline] upload complete: ${summary.rowsSubmitted} row(s) across ${summary.batchCount} " +
            "batch(es), ${summary.failedBatches} batch(es) failed",
    )

    uploader.completePipelineRun(
        runId = runId,
        cardsProcessed = summary.rowsSubmitted,
        status = if (summary.allBatchesOk) "completed" else "failed",
    )

    if (!summary.allBatchesOk) exitProcess(1)
}

private class UploadOptions(
    val input: Path,
    val batchSize: Int,
    val supabaseUrl: String?,
    val dryRun: Boolean,
) {
    companion object {
        fun parse(args: Array<String>): UploadOptions {
            var input: Path? = null
            var batchSize = 1000
            var supabaseUrl: String? = null
            var dryRun = false

            var i = 0
            while (i < args.size) {
                when (val arg = args[i]) {
                    "--in" -> { input = Path.of(requireValue(args, i, arg)); i++ }
                    "--batch-size" -> {
                        batchSize = requireValue(args, i, arg).toIntOrNull()
                            ?: throw IllegalArgumentException("--batch-size must be an integer")
                        i++
                    }
                    "--supabase-url" -> { supabaseUrl = requireValue(args, i, arg); i++ }
                    "--dry-run" -> dryRun = true
                    else -> throw IllegalArgumentException("Unknown argument: $arg")
                }
                i++
            }

            return UploadOptions(
                input = input ?: throw IllegalArgumentException("--in <path> is required"),
                batchSize = batchSize,
                supabaseUrl = supabaseUrl,
                dryRun = dryRun,
            )
        }
    }
}

private const val UPLOAD_USAGE = """
Usage: tag-pipeline upload --in <path> [--batch-size <n>] [--supabase-url <url>] [--dry-run]

Credentials: set SUPABASE_SERVICE_ROLE_KEY (env var, preferred) or add it to the repo-root
local.properties. SUPABASE_URL falls back to the existing local.properties entry when not passed
via --supabase-url or the SUPABASE_URL env var.
"""

private fun requireValue(args: Array<String>, index: Int, flag: String): String =
    args.getOrNull(index + 1) ?: throw IllegalArgumentException("$flag requires a value")
