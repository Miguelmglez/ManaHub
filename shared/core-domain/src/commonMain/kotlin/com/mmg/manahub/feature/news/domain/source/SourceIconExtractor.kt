package com.mmg.manahub.feature.news.domain.source

/** Public source branding discovered from bounded HTML already fetched by the native data source. */
object SourceIconExtractor {
    /** Chooses a channel profile or website icon without accepting active URL schemes. */
    fun extract(html: String, pageUrl: String, youtube: Boolean): String? {
        val base = SourceUrl.parse(pageUrl) ?: return null
        val candidate = if (youtube) {
            openGraphImage(html)
        } else {
            HtmlTags.find(html, "link").firstOrNull { attributes ->
                attributes["rel"]?.lowercase()?.split(' ')?.any { it == "icon" || it == "apple-touch-icon" } == true
            }?.get("href")
        }
        return resolveImageUrl(base, candidate)
    }

    /** Extracts a page's social image, such as a curated Twitch channel avatar. */
    fun pageImage(html: String, pageUrl: String): String? {
        val base = SourceUrl.parse(pageUrl) ?: return null
        return resolveImageUrl(base, openGraphImage(html))
    }

    private fun openGraphImage(html: String): String? =
        HtmlTags.find(html, "meta")
            .firstOrNull { it["property"]?.equals("og:image", ignoreCase = true) == true }
            ?.get("content")

    private fun resolveImageUrl(base: SourceUrl, candidate: String?): String? =
        candidate?.let { FeedAutodiscovery.resolveHttps(base, it.replace("&amp;", "&")) }

    /** Keeps favicon lookup at the source origin rather than a content path. */
    fun favicon(siteUrl: String?): String? = siteUrl?.let(SourceUrl::parse)?.let { it.copy(scheme = "https").origin + "/favicon.ico" }
}
