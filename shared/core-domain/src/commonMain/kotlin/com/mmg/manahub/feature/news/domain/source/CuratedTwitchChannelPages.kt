package com.mmg.manahub.feature.news.domain.source

/** Public Twitch pages whose profile images are shown in Today Trends. */
object CuratedTwitchChannelPages {
    private val pageUrls = linkedMapOf(
        "magic" to "https://www.twitch.tv/magic",
        "mtgjp" to "https://www.twitch.tv/mtgjp",
        "rcastiello" to "https://www.twitch.tv/rcastiello",
        "jirock" to "https://www.twitch.tv/jirock",
    )

    /** Stable channel ids supported by the curated page catalog. */
    val channelIds: List<String> = pageUrls.keys.toList()

    /** Returns the curated public page URL, or null for an unknown channel id. */
    fun urlFor(channelId: String): String? = pageUrls[channelId]
}
