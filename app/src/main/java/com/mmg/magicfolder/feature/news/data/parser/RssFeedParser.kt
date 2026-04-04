package com.mmg.magicfolder.feature.news.data.parser

import android.util.Xml
import com.mmg.magicfolder.feature.news.data.local.NewsArticleEntity
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RssFeedParser @Inject constructor() {

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
                            items += NewsArticleEntity(
                                id          = hashUrl(link),
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
        private val DATE_FORMATS = listOf(
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
            for (fmt in DATE_FORMATS) {
                try {
                    return fmt.parse(trimmed)?.time ?: continue
                } catch (_: Exception) { /* try next */ }
            }
            return 0L
        }

        fun hashUrl(url: String): String {
            val digest = MessageDigest.getInstance("MD5")
            return digest.digest(url.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
