package com.mmg.manahub.feature.news.data.remote

import android.util.Log
import com.mmg.manahub.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

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

/** A page or feed fetched while resolving a new source; [finalUrl] is the URL after redirects. */
data class FetchedPage(val body: String, val finalUrl: String, val contentType: String?)

/** Typed [NewsFeedService.fetchPage] failure; the message never carries the URL. */
class PageFetchException(val reason: Reason, val httpCode: Int? = null) : IOException(reason.name) {
    enum class Reason { NOT_HTTPS, HTTP_ERROR, EMPTY_BODY }
}

/**
 * KMP migration — Hilt→Koin cutover batch 3. Plain class (no `@Inject`/`@Singleton`); built as a
 * native Koin `single` in [com.mmg.manahub.feature.news.di.newsKoinModule].
 *
 * TODO(KMP): port to Ktor's js/wasm engine when News gains a web actual — this class stays
 * OkHttp/androidMain-only for now (see `feature/today/CLAUDE.md`).
 *
 * @param httpsOnly only tests turn this off (MockWebServer serves plain HTTP).
 */
class NewsFeedService(
    private val client: OkHttpClient,
    private val httpsOnly: Boolean = true,
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
                        Result.failure(Exception("Empty response body"))
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
                    // No URL in the message: failures reach Crashlytics as the non-fatal's cause.
                    Result.failure(Exception("HTTP $code"))
                }
            }
        } catch (e: Exception) {
            // Omit the raw URL from the log message to avoid leaking feed endpoints
            // (including any user-added custom feed URLs) into Logcat.
            if (BuildConfig.DEBUG) Log.w("NewsFeedService", "Failed to fetch feed: ${e::class.simpleName}")
            Result.failure(e)
        }
    }

    /** One GET while resolving a new source: HTTPS only (also after redirects), body capped at [MAX_PAGE_BYTES]. */
    suspend fun fetchPage(url: String): Result<FetchedPage> = withContext(Dispatchers.IO) {
        try {
            val httpUrl = url.toHttpUrlOrNull()
                ?: return@withContext Result.failure(PageFetchException(PageFetchException.Reason.HTTP_ERROR))
            if (httpsOnly && !httpUrl.isHttps) {
                return@withContext Result.failure(PageFetchException(PageFetchException.Reason.NOT_HTTPS))
            }
            val request = Request.Builder()
                .url(httpUrl)
                .header("Accept", "text/html, application/rss+xml, application/atom+xml, application/xml;q=0.9, */*;q=0.8")
                .header("Accept-Language", "en")
                // Without the consent cookie EU users get the consent interstitial instead of the channel page.
                .apply { if (isYouTubeHost(httpUrl.host)) header("Cookie", YOUTUBE_CONSENT_COOKIE) }
                .build()
            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url
                when {
                    httpsOnly && !finalUrl.isHttps ->
                        Result.failure(PageFetchException(PageFetchException.Reason.NOT_HTTPS))
                    !response.isSuccessful ->
                        Result.failure(PageFetchException(PageFetchException.Reason.HTTP_ERROR, response.code))
                    else -> {
                        val body = response.peekBody(MAX_PAGE_BYTES).string()
                        if (body.isBlank()) {
                            Result.failure(PageFetchException(PageFetchException.Reason.EMPTY_BODY))
                        } else {
                            Result.success(FetchedPage(body, finalUrl.toString(), response.header("Content-Type")))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w("NewsFeedService", "Failed to fetch page: ${e::class.simpleName}")
            Result.failure(e)
        }
    }

    private fun isYouTubeHost(host: String): Boolean = host == "youtube.com" || host.endsWith(".youtube.com")

    companion object {
        /** Large enough for a YouTube channel page (~1 MB) or a long feed; bounds memory use. */
        const val MAX_PAGE_BYTES = 2L * 1024 * 1024

        /** Google's "reject all" consent cookie; skips the EU consent interstitial on youtube.com. */
        private const val YOUTUBE_CONSENT_COOKIE = "SOCS=CAI"
    }
}
