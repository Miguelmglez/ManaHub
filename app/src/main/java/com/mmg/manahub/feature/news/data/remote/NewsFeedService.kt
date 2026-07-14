package com.mmg.manahub.feature.news.data.remote

import android.util.Log
import com.mmg.manahub.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Outcome of a conditional-GET feed fetch. Callers must branch on this rather than assume a
 * body is always present — a 304 means the feed content is unchanged since the last fetch and
 * the caller should skip parsing/upsert entirely, only advancing its refresh watermark.
 */
sealed class FeedFetchResult {
    /** Normal 200 response: the raw feed body plus any caching headers to persist. */
    data class Fetched(val body: String, val etag: String?, val lastModified: String?) : FeedFetchResult()

    /** HTTP 304 Not Modified — the feed is unchanged since the `etag`/`lastModified` sent. */
    data object NotModified : FeedFetchResult()
}

/**
 * KMP migration — Hilt→Koin cutover batch 3. Plain class (no `@Inject`/`@Singleton`); built as a
 * native Koin `single` in [com.mmg.manahub.feature.news.di.newsKoinModule].
 *
 * TODO(KMP): port to Ktor's js/wasm engine when News gains a web actual — this class stays
 * OkHttp/androidMain-only for now (see the News feature note in the module CLAUDE.md).
 */
class NewsFeedService(
    private val client: OkHttpClient,
) {
    /**
     * Fetches [url], sending conditional-GET headers when [etag]/[lastModified] are supplied so
     * an unchanged feed short-circuits to a cheap 304 instead of re-downloading + re-parsing the
     * whole body.
     */
    suspend fun fetchFeed(
        url: String,
        etag: String? = null,
        lastModified: String? = null,
    ): Result<FeedFetchResult> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
            if (!etag.isNullOrBlank()) requestBuilder.header("If-None-Match", etag)
            if (!lastModified.isNullOrBlank()) requestBuilder.header("If-Modified-Since", lastModified)

            val response = client.newCall(requestBuilder.build()).execute()
            when {
                response.code == 304 -> {
                    response.close()
                    Result.success(FeedFetchResult.NotModified)
                }
                response.isSuccessful -> {
                    val body = response.body?.string() ?: ""
                    if (body.isBlank()) {
                        // A 200 with a blank body is a transient CDN/proxy glitch, never a
                        // genuine feed — real RSS/Atom always has at least an XML declaration or
                        // root element. Treat it as a failure (same as any other fetch failure)
                        // so the watermark is NOT advanced and this source retries next cycle
                        // instead of waiting out the full TTL. A body that legitimately PARSES to
                        // zero items is unaffected — that decision happens downstream, in
                        // fetchAndPersistSource, after a successful Fetched result.
                        Result.failure(Exception("Empty response body for $url"))
                    } else {
                        val result = FeedFetchResult.Fetched(
                            body = body,
                            etag = response.header("ETag"),
                            lastModified = response.header("Last-Modified"),
                        )
                        Result.success(result)
                    }
                }
                else -> {
                    val code = response.code
                    response.close()
                    Result.failure(Exception("HTTP $code for $url"))
                }
            }
        } catch (e: Exception) {
            // Omit the raw URL from the log message to avoid leaking feed endpoints
            // (including any user-added custom feed URLs) into Logcat.
            if (BuildConfig.DEBUG) Log.w("NewsFeedService", "Failed to fetch feed: ${e.message}")
            Result.failure(e)
        }
    }
}
