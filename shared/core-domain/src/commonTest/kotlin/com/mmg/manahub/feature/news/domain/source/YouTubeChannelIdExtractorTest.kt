package com.mmg.manahub.feature.news.domain.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YouTubeChannelIdExtractorTest {

    private val canonicalId = "UC8ZGymAvfP97qJabgqUkz4A"
    private val otherId = "UCLsiaNUb42gRAP7ewbJ0ecQ"

    @Test
    fun given_canonicalLink_then_itWinsOverEveryOtherMarker() {
        val html = """
            <html><head>
            <meta itemprop="identifier" content="$otherId">
            <link href="https://www.youtube.com/channel/$canonicalId" rel="canonical">
            </head><script>{"channelId":"$otherId","externalId":"$otherId"}</script></html>
        """.trimIndent()

        assertEquals(canonicalId, YouTubeChannelIdExtractor.extract(html))
    }

    @Test
    fun given_singleQuotedCanonicalLink_then_theIdIsFound() {
        val html = "<link rel='canonical' href='https://www.youtube.com/channel/$canonicalId'>"

        assertEquals(canonicalId, YouTubeChannelIdExtractor.extract(html))
    }

    @Test
    fun given_onlyIdentifierMeta_then_itIsUsed() {
        val html = """<meta content="$canonicalId" itemprop="identifier">"""

        assertEquals(canonicalId, YouTubeChannelIdExtractor.extract(html))
    }

    @Test
    fun given_onlyExternalId_then_itIsPreferredOverChannelId() {
        val html = """{"channelId":"$otherId","metadata":{"externalId":"$canonicalId"}}"""

        assertEquals(canonicalId, YouTubeChannelIdExtractor.extract(html))
    }

    @Test
    fun given_onlyChannelId_then_itIsUsed() {
        assertEquals(canonicalId, YouTubeChannelIdExtractor.extract("""x "channelId" : "$canonicalId" y"""))
    }

    @Test
    fun given_malformedIds_then_nullIsReturned() {
        val html = """
            <link rel="canonical" href="https://www.youtube.com/@handle">
            <meta itemprop="identifier" content="UCshort">
            {"externalId":"UCtooShort"}
        """.trimIndent()

        assertNull(YouTubeChannelIdExtractor.extract(html))
    }

    @Test
    fun given_anEuConsentPage_then_nullIsReturned() {
        assertNull(YouTubeChannelIdExtractor.extract("<html><form action=\"https://consent.youtube.com/save\"></form></html>"))
    }
}
