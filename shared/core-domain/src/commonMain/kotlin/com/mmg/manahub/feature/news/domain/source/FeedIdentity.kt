package com.mmg.manahub.feature.news.domain.source

/** Dedupe key for feed URLs: scheme, `www.`, case of the host and a trailing slash never make two feeds distinct. */
object FeedIdentity {

    fun of(feedUrl: String): String {
        YouTubeUrls.channelIdFromFeedUrl(feedUrl)?.let { return "youtube:$it" }
        val url = SourceUrl.parse(feedUrl) ?: return feedUrl.trim().lowercase()
        val host = url.host.removePrefix("www.")
        val port = url.port?.let { ":$it" } ?: ""
        val path = url.path.trimEnd('/')
        val query = url.query?.let { "?$it" } ?: ""
        return host + port + path + query
    }
}
