package com.mmg.manahub.feature.news.domain.source

import com.mmg.manahub.core.model.news.ContentSource

/** Channel-level metadata of an RSS 2.0 `<channel>` or Atom `<feed>` (top level only, never an item's). */
data class FeedChannelMeta(
    val title: String?,
    val link: String?,
    val language: String?,
)

/** Pure string inspection of a fetched feed body; no XML parser, so it runs on every target. */
object FeedDocument {

    private val ROOT_ELEMENT = Regex("^<([A-Za-z_][\\w:.-]*)")
    private val FIRST_ITEM = Regex("<(item|entry)[\\s>/]", RegexOption.IGNORE_CASE)
    private val TITLE = Regex("<title(?:\\s[^>]*)?>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)
    private val RSS_LINK = Regex("<link(?:\\s[^>]*)?>([\\s\\S]*?)</link>", RegexOption.IGNORE_CASE)
    private val LANGUAGE = Regex("<language(?:\\s[^>]*)?>([\\s\\S]*?)</language>", RegexOption.IGNORE_CASE)
    private val XML_LANG = Regex("xml:lang\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val CDATA = Regex("<!\\[CDATA\\[([\\s\\S]*?)]]>")
    private val TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")

    /** True when the document's root element is `<rss>`, `<feed>` or `<rdf:RDF>`. */
    fun looksLikeFeed(body: String): Boolean = rootElement(body) in FEED_ROOTS

    fun parseChannelMeta(xml: String): FeedChannelMeta? {
        val root = rootElement(xml) ?: return null
        if (root !in FEED_ROOTS) return null
        val head = FIRST_ITEM.find(xml)?.let { xml.substring(0, it.range.first) } ?: xml
        val title = TITLE.find(head)?.groupValues?.get(1)?.let { cleanText(it) }?.takeIf { it.isNotEmpty() }
        val link = if (root == "feed") atomAlternateLink(head) else rssLink(head)
        val language = LANGUAGE.find(head)?.groupValues?.get(1)?.let { cleanText(it) }
            ?: XML_LANG.find(head)?.groupValues?.get(1)
        return FeedChannelMeta(title = title, link = link, language = supportedLanguage(language))
    }

    /** Maps a raw `<language>` value like `es-ES` to a supported short code, or null. */
    fun supportedLanguage(raw: String?): String? =
        raw?.trim()?.take(2)?.lowercase()?.takeIf { it in ContentSource.SUPPORTED_LANGUAGES }

    private fun rssLink(head: String): String? =
        RSS_LINK.findAll(head)
            .map { cleanText(it.groupValues[1]) }
            .firstOrNull { it.startsWith("http", ignoreCase = true) }

    private fun atomAlternateLink(head: String): String? =
        HtmlTags.find(head, "link")
            .firstOrNull { attrs ->
                val rel = attrs["rel"]?.lowercase()
                (rel == null || rel == "alternate") && attrs["href"] != null &&
                    attrs["type"]?.contains("html", ignoreCase = true) != false
            }
            ?.get("href")
            ?.trim()

    private fun rootElement(body: String): String? {
        var rest = body.take(PROLOG_SCAN_LIMIT).trimStart('\uFEFF')
        while (true) {
            rest = rest.trimStart()
            rest = when {
                rest.startsWith("<?") -> rest.substringAfter("?>", "")
                rest.startsWith("<!--") -> rest.substringAfter("-->", "")
                rest.startsWith("<!") -> rest.substringAfter(">", "")
                else -> break
            }
        }
        return ROOT_ELEMENT.find(rest)?.groupValues?.get(1)?.lowercase()
    }

    private fun cleanText(raw: String): String {
        val unwrapped = CDATA.replace(raw) { it.groupValues[1] }
        return HtmlTags.decodeEntities(TAG.replace(unwrapped, "")).replace(WHITESPACE, " ").trim()
    }

    private val FEED_ROOTS = setOf("rss", "feed", "rdf:rdf")
    private const val PROLOG_SCAN_LIMIT = 16_384
}
