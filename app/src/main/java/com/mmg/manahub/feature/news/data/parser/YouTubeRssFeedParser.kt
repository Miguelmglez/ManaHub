package com.mmg.manahub.feature.news.data.parser

import android.util.Xml
import com.mmg.manahub.feature.news.data.local.NewsVideoEntity
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRssFeedParser @Inject constructor() {

    fun parse(xml: String, sourceId: String, sourceName: String): List<NewsVideoEntity> {
        val videos = mutableListOf<NewsVideoEntity>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var insideEntry = false
        var title = ""
        var link = ""
        var videoId = ""
        var published = ""
        var description = ""
        var thumbnailUrl: String? = null
        var channelName = sourceName
        val now = System.currentTimeMillis()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    when {
                        tag == "entry" -> {
                            insideEntry = true
                            title = ""; link = ""; videoId = ""; published = ""
                            description = ""; thumbnailUrl = null
                        }
                        // Channel name from top-level <author><name>
                        !insideEntry && tag == "name" -> {
                            channelName = readText(parser)
                        }
                        insideEntry -> when (tag) {
                            "title" -> title = readText(parser)
                            "link" -> {
                                link = parser.getAttributeValue(null, "href") ?: readText(parser)
                            }
                            "yt:videoId" -> videoId = readText(parser)
                            "published" -> published = readText(parser)
                            "media:description" -> description = readText(parser)
                            "media:thumbnail" -> {
                                thumbnailUrl = parser.getAttributeValue(null, "url")
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "entry" && insideEntry) {
                        insideEntry = false
                        if (videoId.isNotBlank() && title.isNotBlank()) {
                            val thumb = thumbnailUrl
                                ?: "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
                            val videoUrl = link.ifBlank { "https://www.youtube.com/watch?v=$videoId" }
                            videos += NewsVideoEntity(
                                videoId     = videoId,
                                title       = title.trim(),
                                description = description.take(500).trim(),
                                imageUrl    = thumb,
                                publishedAt = RssFeedParser.parseDate(published),
                                sourceName  = sourceName,
                                sourceId    = sourceId,
                                url         = videoUrl,
                                channelName = channelName,
                                duration    = null,
                                fetchedAt   = now,
                            )
                        }
                    }
                }
            }
        }
        return videos
    }

    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.TEXT      -> sb.append(parser.text)
                XmlPullParser.CDSECT   -> sb.append(parser.text)
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG   -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return sb.toString()
    }
}
