package com.mmg.manahub.tools.tagpipeline.pipeline

import com.mmg.manahub.tools.tagpipeline.io.readJsonl
import com.mmg.manahub.tools.tagpipeline.model.CardStrategyTagsRow
import java.nio.file.Path

/**
 * `--since` watermark support (plan §5 Phase 5d, folded into this run per the RUN 5 brief: "the CLI
 * needs to accept and honor the parameter against a local/injectable 'already processed' set").
 *
 * The watermark manifest is simply a PREVIOUS pipeline run's own JSONL output — every row already
 * carries `oracle_id` + `pipeline_version`, so no separate manifest format is needed. This is the
 * simplest design that satisfies the plan's own wording exactly: "only reprocesses cards absent from
 * or older than that watermark" — "absent" = oracle_id not in the manifest (a genuinely new card);
 * "older" = the manifest's `pipeline_version` for that oracle_id is lower than the CURRENT run's
 * `pipeline_version` (i.e. the rule engine/mapping tables changed since the card was last processed,
 * per [com.mmg.manahub.tools.tagpipeline.pipeline.PIPELINE_VERSION]'s own KDoc).
 *
 * The actual `pipeline_runs` Supabase table that PERSISTS this watermark between runs (plan §5
 * Phase 5b/5d) is the parallel `backend-supabase-expert` agent's responsibility — this class only
 * implements the CLI-side mechanism the plan explicitly asked for; wiring "fetch the last run's
 * output/watermark from Supabase automatically" is future plumbing, not blocking this run.
 */
object SinceWatermark {

    /** Loads a previous run's JSONL output into `oracle_id -> pipeline_version`. Never throws — a
     *  missing/corrupt manifest degrades to "process everything" (same "fail open, never abort the
     *  whole run" posture as the EDHREC/Oracle-Tags fetches). */
    fun loadManifest(previousOutputPath: Path): Map<String, Int> = try {
        readJsonl(previousOutputPath, CardStrategyTagsRow.serializer(), gzip = false)
            .associate { it.oracleId to it.pipelineVersion }
    } catch (e: Exception) {
        System.err.println(
            "[tag-pipeline] --since manifest at $previousOutputPath unreadable (${e.message}); " +
                "processing all cards this run",
        )
        emptyMap()
    }

    /** True when [oracleId] should be (re)processed this run: absent from [manifest], or last
     *  processed at an OLDER pipeline version than [currentPipelineVersion]. */
    fun shouldReprocess(oracleId: String, currentPipelineVersion: Int, manifest: Map<String, Int>): Boolean {
        val lastProcessedVersion = manifest[oracleId] ?: return true
        return lastProcessedVersion < currentPipelineVersion
    }
}
