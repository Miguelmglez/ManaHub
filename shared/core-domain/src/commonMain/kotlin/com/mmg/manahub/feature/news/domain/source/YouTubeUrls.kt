package com.mmg.manahub.feature.news.domain.source

object YouTubeUrls {
    /** Channel ids are case-sensitive and always `UC` + 22 chars. */
    val CHANNEL_ID = Regex("^UC[\\w-]{22}$")

    fun feedUrl(channelId: String): String = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"

    fun channelUrl(channelId: String): String = "https://www.youtube.com/channel/$channelId"

    fun isYouTubeHost(host: String): Boolean = host == "youtube.com" || host.endsWith(".youtube.com")

    fun isChannelId(value: String): Boolean = CHANNEL_ID.matches(value)

    /** The channel id of a `…/feeds/videos.xml?channel_id=UC…` feed URL, or null for any other URL. */
    fun channelIdFromFeedUrl(feedUrl: String): String? {
        val url = SourceUrl.parse(feedUrl) ?: return null
        if (!isYouTubeHost(url.host) || url.path != "/feeds/videos.xml") return null
        return url.queryParameter("channel_id")?.takeIf { isChannelId(it) }
    }
}
