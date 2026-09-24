package com.mmg.manahub.feature.news.domain.source

import com.mmg.manahub.core.model.news.SourceResolveError
import com.mmg.manahub.core.model.news.SourceResolveException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceInputClassifierTest {

    private val channelId = "UC8ZGymAvfP97qJabgqUkz4A"

    private fun classify(input: String): SourceInput = SourceInputClassifier.classify(input).getOrThrow()

    private fun errorOf(input: String): SourceResolveError? =
        (SourceInputClassifier.classify(input).exceptionOrNull() as? SourceResolveException)?.error

    @Test
    fun given_blankInput_then_invalidInput() {
        assertEquals(SourceResolveError.INVALID_INPUT, errorOf("   "))
    }

    @Test
    fun given_bareHandle_then_youTubeHandlePageOnWww() {
        assertEquals(SourceInput.YouTubeHandle("https://www.youtube.com/@MTGGoldfish"), classify("  @MTGGoldfish "))
    }

    @Test
    fun given_bareChannelId_then_youTubeChannelId() {
        assertEquals(SourceInput.YouTubeChannelId(channelId), classify(channelId))
    }

    @Test
    fun given_channelIdWithWrongCase_then_itIsNotTreatedAsAChannelId() {
        assertEquals(SourceResolveError.INVALID_INPUT, errorOf("uc8zgymavfp97qjabgquKz4a"))
    }

    @Test
    fun given_youTubeFeedUrl_then_youTubeFeedWithItsChannelId() {
        assertEquals(
            SourceInput.YouTubeFeed(channelId),
            classify("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"),
        )
    }

    @Test
    fun given_channelUrlWithMixedCaseHost_then_channelIdIsKeptCaseSensitive() {
        assertEquals(SourceInput.YouTubeChannelId(channelId), classify("https://YouTube.com/channel/$channelId/videos"))
    }

    @Test
    fun given_channelIdLongerThan24Chars_then_itIsNotTruncated() {
        assertEquals(SourceResolveError.YOUTUBE_CHANNEL_NOT_FOUND, errorOf("https://www.youtube.com/channel/${channelId}X"))
    }

    @Test
    fun given_handleUrlWithSubPage_then_handlePageWithoutTheSubPage() {
        assertEquals(
            SourceInput.YouTubeHandle("https://www.youtube.com/@CommandZone"),
            classify("m.youtube.com/@CommandZone/videos"),
        )
    }

    @Test
    fun given_legacyCustomAndUserUrls_then_youTubeHandlePages() {
        assertEquals(SourceInput.YouTubeHandle("https://www.youtube.com/c/Tolarian"), classify("https://www.youtube.com/c/Tolarian"))
        assertEquals(SourceInput.YouTubeHandle("https://www.youtube.com/user/wotc"), classify("https://youtube.com/user/wotc"))
    }

    @Test
    fun given_aVideoLink_then_channelNotFoundWithoutFetching() {
        assertEquals(SourceResolveError.YOUTUBE_CHANNEL_NOT_FOUND, errorOf("https://www.youtube.com/watch?v=abc"))
        assertEquals(SourceResolveError.YOUTUBE_CHANNEL_NOT_FOUND, errorOf("https://youtu.be/abc"))
    }

    @Test
    fun given_urlWithoutScheme_then_httpsIsAssumed() {
        assertEquals(SourceInput.Web("https://www.mtggoldfish.com/feed"), classify("www.mtggoldfish.com/feed"))
    }

    @Test
    fun given_httpUrl_then_itIsUpgradedToHttps() {
        assertEquals(SourceInput.Web("https://example.com/blog"), classify("http://example.com/blog#top"))
    }

    @Test
    fun given_nonWebScheme_then_notHttps() {
        assertEquals(SourceResolveError.NOT_HTTPS, errorOf("ftp://example.com/feed"))
    }

    @Test
    fun given_textThatIsNotALink_then_invalidInput() {
        assertEquals(SourceResolveError.INVALID_INPUT, errorOf("magic news"))
        assertEquals(SourceResolveError.INVALID_INPUT, errorOf("localhost"))
    }

    @Test
    fun given_youTubePlaylistFeed_then_itIsFetchedAsAPlainFeed() {
        assertEquals(
            SourceInput.Web("https://www.youtube.com/feeds/videos.xml?playlist_id=PL1"),
            classify("https://youtube.com/feeds/videos.xml?playlist_id=PL1"),
        )
    }

    @Test
    fun given_sourceUrlParse_then_queryParameterIsReadVerbatim() {
        val url = SourceUrl.parse("https://Example.com:8443/a/b?x=1&channel_id=UC1")
        assertEquals("example.com", url?.host)
        assertEquals(8443, url?.port)
        assertEquals("UC1", url?.queryParameter("channel_id"))
        assertNull(url?.queryParameter("missing"))
    }
}
