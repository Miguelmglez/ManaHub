package com.mmg.manahub.feature.news.domain.source

/** RSS/Atom autodiscovery (`<link rel="alternate" type="application/rss+xml|atom+xml">`) plus common fallback paths. */
object FeedAutodiscovery {

    val FALLBACK_FEED_PATHS = listOf("/feed", "/feed/", "/rss", "/rss.xml", "/atom.xml", "/index.xml")

    private val FEED_TYPES = setOf("application/rss+xml", "application/atom+xml")

    /** Discovered feed URLs resolved against [pageUrl], HTTPS only, main feed before comment feeds. */
    fun discover(html: String, pageUrl: String): List<String> {
        val base = SourceUrl.parse(pageUrl) ?: return emptyList()
        return HtmlTags.find(html, "link")
            .filter { attrs ->
                attrs["rel"]?.lowercase()?.split(' ')?.contains("alternate") == true &&
                    attrs["type"]?.trim()?.lowercase() in FEED_TYPES
            }
            .mapNotNull { attrs -> attrs["href"]?.trim()?.takeIf { it.isNotEmpty() } }
            .mapNotNull { href -> resolveHttps(base, href) }
            .distinct()
            .sortedBy { it.contains("/comments/", ignoreCase = true) }
            .toList()
    }

    fun fallbackFeedUrls(pageUrl: String): List<String> {
        val base = SourceUrl.parse(pageUrl) ?: return emptyList()
        return FALLBACK_FEED_PATHS.map { base.origin + it }
    }

    /** Resolves [href] (absolute, protocol-relative, root- or document-relative) to an HTTPS URL. */
    fun resolveHttps(base: SourceUrl, href: String): String? {
        val absolute = when {
            href.startsWith("https://", ignoreCase = true) -> href
            href.startsWith("http://", ignoreCase = true) -> "https://" + href.substring("http://".length)
            href.startsWith("//") -> "https:$href"
            SourceUrl.hasScheme(href) || href.contains(':') && href.substringBefore(':').none { it == '/' } -> return null
            href.startsWith("/") -> base.origin + href
            else -> base.origin + base.path.substringBeforeLast('/', "") + "/" + href.removePrefix("./")
        }
        val parsed = SourceUrl.parse(absolute) ?: return null
        return parsed.copy(scheme = "https").toString()
    }
}
