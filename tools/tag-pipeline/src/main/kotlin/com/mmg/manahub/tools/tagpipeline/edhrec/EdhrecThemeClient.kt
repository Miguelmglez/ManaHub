package com.mmg.manahub.tools.tagpipeline.edhrec

import com.mmg.manahub.core.data.remote.edhrec.EdhrecThemePageDto
import com.mmg.manahub.tools.tagpipeline.io.DiskCache
import com.mmg.manahub.tools.tagpipeline.io.PIPELINE_JSON
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Fetches + caches EDHREC theme/tag pages (plan D6). **Every fetch is treated as fallible**: EDHREC
 * is unofficial/undocumented (`json.edhrec.com` has no published API contract), so a failure here
 * must degrade the pipeline to Scryfall-tags-only for that theme, never crash the whole run — this
 * mirrors the plan's explicit requirement ("treat every fetch as fallible... never crash the pipeline
 * if EDHREC is down").
 *
 * A courtesy delay is applied between requests ([REQUEST_DELAY_MS]) — this is a ~20-100 request loop
 * (one per [com.mmg.manahub.feature.decks.domain.engine.EDHREC_THEME_SLUGS] entry), not a per-card
 * loop, but EDHREC documents no rate limit at all, so pacing it like a considerate client (mirrors
 * `ScryfallRequestQueue`'s spirit, applied to a different, undocumented host) costs almost nothing
 * and avoids ever looking like abusive traffic.
 *
 * A browser-like User-Agent + `Referer` header are REQUIRED — verified live (2026-07-20): a request
 * without them 403s even against a real, existing page.
 */
class EdhrecThemeClient(
    private val cache: DiskCache,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {

    /** Fetches (cache-first) the theme page for [slug], returning `null` on ANY failure (network
     *  error, non-200, unparsable body) rather than throwing — callers should treat `null` as "skip
     *  this theme this run," never as a reason to abort. */
    fun fetchThemePageOrNull(slug: String): EdhrecThemePageDto? {
        val cacheFile = "edhrec-tag-$slug.json"
        val cached = cache.readText(cacheFile)
        if (cached != null) {
            return runCatching { PIPELINE_JSON.decodeFromString(EdhrecThemePageDto.serializer(), cached) }
                .getOrNull()
        }

        return try {
            val request = HttpRequest.newBuilder(URI.create("$BASE_URL/pages/tags/$slug.json"))
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://edhrec.com/")
                .header("Accept", "application/json")
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                System.err.println("[tag-pipeline] EDHREC theme '$slug' returned HTTP ${response.statusCode()}, skipping")
                return null
            }
            cache.writeText(cacheFile, response.body())
            PIPELINE_JSON.decodeFromString(EdhrecThemePageDto.serializer(), response.body())
        } catch (e: Exception) {
            System.err.println("[tag-pipeline] EDHREC theme '$slug' fetch failed, skipping: ${e.message}")
            null
        } finally {
            Thread.sleep(REQUEST_DELAY_MS)
        }
    }

    companion object {
        const val BASE_URL = "https://json.edhrec.com"
        const val USER_AGENT =
            "Mozilla/5.0 (compatible; ManaHubTagPipeline/1; +https://github.com/Miguelmglez/ManaHub)"
        const val REQUEST_DELAY_MS = 250L
    }
}
