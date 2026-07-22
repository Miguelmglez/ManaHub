package com.mmg.manahub.tools.tagpipeline.io

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream

/**
 * The shared [Json] instance every DTO decode/encode in this module uses.
 *  - `ignoreUnknownKeys`/`isLenient`: tolerant of upstream fields this pipeline doesn't declare
 *    (bulk-data JSON evolves independently of this codebase).
 *  - `encodeDefaults = true`: found via the real smoke-test run (RUN 5) — kotlinx.serialization
 *    OMITS a field from the output entirely when it equals its default value UNLESS this is set.
 *    `CardStrategyTagsRow.themes`/`archetypes` default to `emptyMap()`, so without this flag most
 *    rows (any card EDHREC never ranks — the vast majority) would silently be missing the `themes`/
 *    `archetypes` keys altogether, breaking the "every row has this exact shape" contract the
 *    Supabase `card_strategy_tags` JSONB payload (plan §5 Phase 5b) is expected to rely on.
 */
val PIPELINE_JSON: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

/**
 * Lazily streams a JSONL(.gz) file as a `Sequence<T>` — one JSON object decoded per non-blank line.
 * Never loads the whole file into memory: appropriate for the ~30k-line Oracle Cards / Oracle Tags
 * bulk files. A malformed line is skipped (not fatal — bulk data occasionally carries a stray
 * encoding artifact; losing one card/tag row must never abort a multi-hour production run).
 *
 * [gzip] controls whether the file is decompressed while streaming (Scryfall's `jsonl_download_uri`
 * files are always `.gz`).
 */
fun <T> readJsonl(path: Path, serializer: KSerializer<T>, gzip: Boolean = true): Sequence<T> {
    val rawInput = Files.newInputStream(path)
    val input = if (gzip) GZIPInputStream(rawInput) else rawInput
    val reader = input.bufferedReader(StandardCharsets.UTF_8)
    return reader.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            runCatching { PIPELINE_JSON.decodeFromString(serializer, line) }.getOrNull()
        }
        // Closing the reader when the sequence is exhausted would require a `use{}` around the
        // whole terminal operation at the call site; instead each caller is expected to fully
        // drain the sequence in one pass (true of every call site in this pipeline) and the
        // process exits shortly after — an acceptable trade-off for a short-lived CLI.
}

/**
 * Writes a sequence of rows as JSONL to [outputPath], one JSON object per line. Writes to a temp
 * file first and atomically renames on success (mirrors [DiskCache.writeText]'s crash-safety
 * rationale — a partial JSONL file must never look like a complete, valid pipeline output).
 */
fun <T> writeJsonl(outputPath: Path, serializer: KSerializer<T>, rows: Sequence<T>): Long {
    val parent = outputPath.parent ?: Path.of(".")
    Files.createDirectories(parent)
    val tmp = Files.createTempFile(
        parent,
        outputPath.fileName.toString() + ".",
        ".tmp",
    )
    var count = 0L
    Files.newOutputStream(tmp).use { out: OutputStream ->
        val writer: BufferedWriter = out.bufferedWriter(StandardCharsets.UTF_8)
        rows.forEach { row ->
            writer.write(PIPELINE_JSON.encodeToString(serializer, row))
            writer.newLine()
            count++
        }
        writer.flush()
    }
    Files.move(tmp, outputPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    return count
}
