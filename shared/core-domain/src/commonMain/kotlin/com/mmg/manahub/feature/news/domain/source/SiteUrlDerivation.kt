package com.mmg.manahub.feature.news.domain.source

/** The page a source's "Open site"/"Open channel" action opens. */
object SiteUrlDerivation {

    /** YouTube feed → its channel page; else the feed's HTTPS channel link; else the feed's origin. */
    fun derive(feedUrl: String, channelLink: String?): String? {
        YouTubeUrls.channelIdFromFeedUrl(feedUrl)?.let { return YouTubeUrls.channelUrl(it) }
        httpsLink(channelLink)?.let { return it }
        return SourceUrl.parse(feedUrl)?.takeIf { it.scheme == "https" }?.origin
    }

    /** [link] as an absolute HTTPS URL (`http://` upgraded), or null. */
    fun httpsLink(link: String?): String? {
        val parsed = link?.let { SourceUrl.parse(it) } ?: return null
        return when (parsed.scheme) {
            "https", "http" -> parsed.copy(scheme = "https").toString()
            else -> null
        }
    }
}
