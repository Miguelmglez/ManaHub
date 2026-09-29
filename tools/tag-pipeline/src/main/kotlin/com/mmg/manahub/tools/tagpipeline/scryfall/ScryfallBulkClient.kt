package com.mmg.manahub.tools.tagpipeline.scryfall

import com.mmg.manahub.core.data.remote.dto.CardDto
import com.mmg.manahub.tools.tagpipeline.io.DiskCache
import com.mmg.manahub.tools.tagpipeline.io.PIPELINE_JSON
import com.mmg.manahub.tools.tagpipeline.io.readJsonl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.security.MessageDigest
import java.nio.file.StandardCopyOption

/**
 * Downloads + caches Scryfall's bulk-data files (`oracle_cards`, `oracle_tags`) and streams their
 * rows via the EXACT same [CardDto] the live app's `ScryfallClient`/`CardDtoMapper` use — bulk-data
 * files are literally arrays of the same "Card object" the REST API returns per Scryfall's own docs,
 * so no separate bulk-specific DTO is needed for cards (verified: a real downloaded `oracle_cards`
 * row decodes cleanly against the app's [CardDto]).
 *
 * **Politeness**: a bulk-data download is ONE request per file (not a per-card loop — the plan's own
 * distinction: "this is a bulk *file* download not a rate-limited API loop"), with a descriptive
 * User-Agent identifying this tool, and results cached to disk so a dev re-run never re-downloads.
 */
class ScryfallBulkClient(
    private val cache: DiskCache,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {

    /** Fetches the bulk-data listing and returns the download URI for [type] (e.g. `"oracle_cards"`,
     *  `"oracle_tags"`). Prefers `jsonl_download_uri` (the JSONL/gzip format Scryfall is
     *  standardizing on) over the legacy `download_uri` JSON-array format. */
    fun resolveDownloadUri(type: String): String {
        return resolveBulkEntry(type).first
    }

    private fun resolveBulkEntry(type: String): Pair<String, String> {
        val request = HttpRequest.newBuilder(URI.create(BULK_DATA_INDEX_URL))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) {
            "Scryfall bulk-data index returned HTTP ${response.statusCode()}"
        }
        val index = PIPELINE_JSON.decodeFromString(BulkDataIndexDto.serializer(), response.body())
        val entry = index.data.firstOrNull { it.type == type }
            ?: error("No bulk-data entry of type '$type' in the Scryfall bulk-data index")
        val uri = entry.jsonlDownloadUri
            ?: entry.downloadUri
            ?: error("Bulk-data entry '$type' has neither jsonl_download_uri nor download_uri")
        val updatedAt = entry.updatedAt?.takeIf(String::isNotBlank)
            ?: error("Bulk-data entry '$type' has no updated_at; refusing an unversioned cache")
        return uri to updatedAt
    }

    /** Downloads [uri] to [cache]'s [cacheFileName] UNLESS it already exists — streams straight to
     *  disk (never buffers the whole file in memory; these files can exceed 150 MB uncompressed). */
    fun downloadIfAbsent(uri: String, cacheFileName: String): Path {
        val dest = cache.path(cacheFileName)
        if (Files.exists(dest) && Files.size(dest) > 0) return dest
        val request = HttpRequest.newBuilder(URI.create(uri))
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()
        val temp = Files.createTempFile(dest.parent, "bulk-", ".tmp")
        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(temp))
            check(response.statusCode() == 200) { "Download of $uri returned HTTP ${response.statusCode()}" }
            Files.move(temp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temp)
        }
        return dest
    }

    private fun versionedCacheName(type: String, uri: String, updatedAt: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$updatedAt\n$uri".toByteArray(Charsets.UTF_8))
        val version = bytes.take(12).joinToString("") { "%02x".format(it) }
        return "$type-$version.jsonl.gz"
    }

    /** Convenience: resolve + download-if-absent + stream-decode `oracle_cards` as [CardDto]. */
    fun streamOracleCards(): Sequence<CardDto> {
        val (uri, updatedAt) = resolveBulkEntry("oracle_cards")
        val file = downloadIfAbsent(uri, versionedCacheName("oracle-cards", uri, updatedAt))
        return readJsonl(file, CardDto.serializer(), gzip = true, strict = true)
    }

    /** Convenience: resolve + download-if-absent + stream-decode `oracle_tags` as [OracleTagDto].
     *  Returns an EMPTY sequence (never throws) when the fetch fails for any reason — the Oracle
     *  Tags source is a community/unofficial-adjacent addition (plan: "not part of Scryfall's
     *  formally documented grammar", same fallibility class the app's own `otag:` query fallback
     *  already treats it as), so its absence must degrade the pipeline to rule-engine-only tags,
     *  never abort the whole run. */
    fun streamOracleTags(): Sequence<OracleTagDto> {
        val (uri, updatedAt) = resolveBulkEntry("oracle_tags")
        val file = downloadIfAbsent(uri, versionedCacheName("oracle-tags", uri, updatedAt))
        return readJsonl(file, OracleTagDto.serializer(), gzip = true, strict = true)
    }

    fun streamOracleTagsOrEmpty(): Sequence<OracleTagDto> = runCatching { streamOracleTags() }.getOrElse { emptySequence() }

    companion object {
        const val BULK_DATA_INDEX_URL = "https://api.scryfall.com/bulk-data"
        const val USER_AGENT = "ManaHubTagPipeline/1 (+https://github.com/Miguelmglez/ManaHub)"
    }
}

@Serializable
private data class BulkDataIndexDto(@SerialName("data") val data: List<BulkDataEntryDto> = emptyList())

@Serializable
private data class BulkDataEntryDto(
    @SerialName("type") val type: String,
    @SerialName("download_uri") val downloadUri: String? = null,
    @SerialName("jsonl_download_uri") val jsonlDownloadUri: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
