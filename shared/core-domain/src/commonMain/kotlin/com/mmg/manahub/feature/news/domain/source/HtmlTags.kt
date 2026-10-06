package com.mmg.manahub.feature.news.domain.source

/** Attribute-order-independent scanner for void tags like `<link>`/`<meta>` in fetched HTML. */
internal object HtmlTags {

    private val ATTRIBUTE = Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""")

    fun find(html: String, tagName: String): Sequence<Map<String, String>> =
        Regex("<$tagName\\b([^>]*)>", RegexOption.IGNORE_CASE)
            .findAll(html)
            .map { parseAttributes(it.groupValues[1]) }

    private fun parseAttributes(raw: String): Map<String, String> =
        ATTRIBUTE.findAll(raw).associate { match ->
            val value = match.groupValues[2].ifEmpty { match.groupValues[3].ifEmpty { match.groupValues[4] } }
            match.groupValues[1].lowercase() to decodeEntities(value)
        }

    fun decodeEntities(text: String): String =
        text.replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#x2F;", "/")
            .replace("&#47;", "/")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
}
