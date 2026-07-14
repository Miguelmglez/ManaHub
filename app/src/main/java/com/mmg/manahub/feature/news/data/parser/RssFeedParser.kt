package com.mmg.manahub.feature.news.data.parser

import android.util.Xml
import com.mmg.manahub.core.data.local.entity.NewsArticleEntity
import com.mmg.manahub.core.model.news.NewsFilterPrefs
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URI
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** KMP migration — Hilt→Koin cutover batch 3. Plain class; a native Koin `single`. */
class RssFeedParser {

    fun parse(xml: String, sourceId: String, sourceName: String): List<NewsArticleEntity> {
        val items = mutableListOf<NewsArticleEntity>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var isAtom = false
        var insideItem = false
        var title = ""
        var link = ""
        var description = ""
        var pubDate = ""
        var author: String? = null
        var imageUrl: String? = null
        var guid = ""
        var guidIsPermaLink = false
        val now = System.currentTimeMillis()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    when {
                        tag == "feed" -> isAtom = true
                        tag == "item" || (isAtom && tag == "entry") -> {
                            insideItem = true
                            title = ""; link = ""; description = ""; pubDate = ""
                            author = null; imageUrl = null
                            guid = ""; guidIsPermaLink = false
                        }
                        insideItem -> when (tag) {
                            "title" -> title = readText(parser)
                            "link" -> {
                                if (isAtom) {
                                    link = parser.getAttributeValue(null, "href") ?: readText(parser)
                                } else {
                                    link = readText(parser)
                                }
                            }
                            "guid" -> {
                                // RSS 2.0 <guid isPermaLink="true|false">…</guid>. Per spec, an
                                // absent attribute defaults to "true", but real-world feeds are
                                // inconsistent — be conservative and only treat an attribute-less
                                // guid as usable when its own content looks like a URL, otherwise
                                // fall back to <link> (a non-permalink guid is often an internal
                                // UUID/DB id, not a stable public identifier for this article).
                                val permaLinkAttr = parser.getAttributeValue(null, "isPermaLink")
                                val text = readText(parser).trim()
                                if (guid.isEmpty() && text.isNotBlank()) {
                                    guid = text
                                    guidIsPermaLink = when (permaLinkAttr) {
                                        "true" -> true
                                        "false" -> false
                                        else -> looksLikeUrl(text)
                                    }
                                }
                            }
                            "description", "summary", "content:encoded" -> {
                                val text = readText(parser)
                                if (description.isEmpty()) description = text
                                if (imageUrl == null) imageUrl = extractImageFromHtml(text)
                            }
                            "pubDate", "published", "updated" -> {
                                if (pubDate.isEmpty()) pubDate = readText(parser)
                            }
                            "author", "dc:creator" -> {
                                if (author == null) author = readText(parser)
                            }
                            "media:thumbnail" -> {
                                if (imageUrl == null) {
                                    imageUrl = parser.getAttributeValue(null, "url")
                                }
                            }
                            "media:content" -> {
                                if (imageUrl == null) {
                                    val type = parser.getAttributeValue(null, "medium")
                                        ?: parser.getAttributeValue(null, "type") ?: ""
                                    if (type.contains("image") || type == "image") {
                                        imageUrl = parser.getAttributeValue(null, "url")
                                    }
                                }
                            }
                            "enclosure" -> {
                                if (imageUrl == null) {
                                    val type = parser.getAttributeValue(null, "type") ?: ""
                                    if (type.startsWith("image/")) {
                                        imageUrl = parser.getAttributeValue(null, "url")
                                    }
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name
                    if ((tag == "item" || (isAtom && tag == "entry")) && insideItem) {
                        insideItem = false
                        if (title.isNotBlank() && link.isNotBlank()) {
                            // Prefer a true permalink <guid> over <link> for dedup identity — some
                            // publishers rotate tracking params on <link> across re-syndication
                            // but keep the guid stable.
                            val hashSource = if (guidIsPermaLink && guid.isNotBlank()) guid else link
                            items += NewsArticleEntity(
                                id          = hashUrl(normalizeUrlForHash(hashSource)),
                                title       = title.trim(),
                                description = stripHtml(description).take(500),
                                imageUrl    = imageUrl,
                                publishedAt = parseDate(pubDate),
                                sourceName  = sourceName,
                                sourceId    = sourceId,
                                url         = link.trim(),
                                author      = author?.trim(),
                                fetchedAt   = now,
                            )
                        }
                    }
                }
            }
        }
        return items
    }

    /**
     * Best-effort detection of a top-level RSS 2.0 `<channel><language>` element (ignores any
     * `<language>` nested inside an item/entry). Returns a 2-letter code only when it's one of
     * [NewsFilterPrefs.SUPPORTED_NEWS_LANGUAGES]; null otherwise (Atom/YouTube feeds have no such
     * element, and this must never throw — it's a nice-to-have UI pre-select, not load-bearing).
     */
    fun detectChannelLanguage(xml: String): String? = try {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var isAtom = false
        var insideItem = false
        var detected: String? = null

        loop@ while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    when {
                        tag == "feed" -> isAtom = true
                        tag == "item" || (isAtom && tag == "entry") -> insideItem = true
                        !insideItem && tag == "language" -> {
                            val code = readText(parser).trim().take(2).lowercase(Locale.ENGLISH)
                            detected = code.takeIf { it in NewsFilterPrefs.SUPPORTED_NEWS_LANGUAGES }
                            break@loop
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name
                    if (tag == "item" || (isAtom && tag == "entry")) insideItem = false
                }
            }
        }
        detected
    } catch (_: Exception) {
        null
    }

    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.TEXT    -> sb.append(parser.text)
                XmlPullParser.CDSECT -> sb.append(parser.text)
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG   -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return sb.toString()
    }

    private fun looksLikeUrl(text: String): Boolean =
        text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)

    private fun extractImageFromHtml(html: String): String? {
        val regex = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        return regex.find(html)?.groupValues?.getOrNull(1)
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        // SimpleDateFormat is NOT thread-safe — create fresh instances per call
        // so concurrent coroutines (one per RSS source) don't corrupt shared state.
        private fun buildDateFormats() = listOf(
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),
        )

        fun parseDate(dateStr: String): Long {
            if (dateStr.isBlank()) return 0L
            val trimmed = dateStr.trim()
            for (fmt in buildDateFormats()) {
                try {
                    return fmt.parse(trimmed)?.time ?: continue
                } catch (_: Exception) { /* try next */ }
            }
            return 0L
        }

        /** Query params stripped from the dedup-hash input (exact name match, case-insensitive). */
        private val TRACKING_PARAMS = setOf(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "fbclid", "gclid", "ref",
        )

        /**
         * Normalizes a URL before it's fed into [hashUrl] so trivial variants of the same article
         * (a different tracking-param combination, a `#anchor`, or a differently-cased scheme/host)
         * dedup to the same id. Only the scheme and host are lowercased — the path is left
         * case-sensitive (some servers distinguish it) and any non-tracking query params are kept
         * as-is, unsorted.
         *
         * NOTE: this only affects NEW rows going forward — existing rows already hashed under the
         * old, unnormalized scheme age out naturally via the 7-day eviction (`evictArticlesBefore`),
         * so there is a transient window (≤7 days from this change shipping) where an old and a new
         * hash of the same article can briefly coexist. No backfill/rewrite of existing rows.
         */
        internal fun normalizeUrlForHash(url: String): String {
            val trimmed = url.trim()
            return try {
                val uri = URI(trimmed)
                val scheme = uri.scheme?.lowercase(Locale.ENGLISH) ?: return trimmed
                val host = uri.host?.lowercase(Locale.ENGLISH) ?: return trimmed
                val port = if (uri.port != -1) ":${uri.port}" else ""
                val path = uri.rawPath ?: ""
                val query = uri.rawQuery?.let { stripTrackingParams(it) }
                buildString {
                    append(scheme).append("://").append(host).append(port).append(path)
                    if (!query.isNullOrEmpty()) append('?').append(query)
                }
            } catch (_: Exception) {
                trimmed
            }
        }

        private fun stripTrackingParams(rawQuery: String): String =
            rawQuery.split("&")
                .filter { param ->
                    val key = param.substringBefore("=").lowercase(Locale.ENGLISH)
                    key !in TRACKING_PARAMS
                }
                .joinToString("&")

        /**
         * Produces a compact, stable Room primary key from an (already-normalized) article URL.
         * MD5 is used intentionally here for non-cryptographic deduplication only —
         * no secret data is hashed and collision resistance is not a security requirement.
         * Security scanners may flag MD5; this comment documents the deliberate choice.
         */
        @Suppress("InsecureCryptoUsage")
        fun hashUrl(url: String): String {
            val digest = MessageDigest.getInstance("MD5")
            return digest.digest(url.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
