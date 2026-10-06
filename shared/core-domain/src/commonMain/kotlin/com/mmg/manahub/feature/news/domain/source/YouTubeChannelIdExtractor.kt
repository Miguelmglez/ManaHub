package com.mmg.manahub.feature.news.domain.source

/** Finds the canonical `UC…` channel id in a fetched YouTube channel page, most reliable marker first. */
object YouTubeChannelIdExtractor {

    private val CHANNEL_URL = Regex("/channel/(UC[\\w-]{22})(?![\\w-])")
    private val EXTERNAL_ID = Regex("\"externalId\"\\s*:\\s*\"(UC[\\w-]{22})\"")
    private val CHANNEL_ID = Regex("\"channelId\"\\s*:\\s*\"(UC[\\w-]{22})\"")

    fun extract(html: String): String? =
        fromCanonicalLink(html)
            ?: fromIdentifierMeta(html)
            ?: EXTERNAL_ID.find(html)?.groupValues?.get(1)
            ?: CHANNEL_ID.find(html)?.groupValues?.get(1)

    private fun fromCanonicalLink(html: String): String? =
        HtmlTags.find(html, "link")
            .firstOrNull { attrs -> attrs["rel"]?.lowercase()?.split(' ')?.contains("canonical") == true }
            ?.get("href")
            ?.let { CHANNEL_URL.find(it)?.groupValues?.get(1) }

    private fun fromIdentifierMeta(html: String): String? =
        HtmlTags.find(html, "meta")
            .firstOrNull { attrs -> attrs["itemprop"] == "identifier" || attrs["itemprop"] == "channelId" }
            ?.get("content")
            ?.trim()
            ?.takeIf { YouTubeUrls.isChannelId(it) }
}
