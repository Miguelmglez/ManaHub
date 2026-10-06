package com.mmg.manahub.feature.news.domain.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceIconExtractorTest {
    @Test
    fun websiteIconResolvesRelativeAndNormalizesFallbackOrigin() {
        assertEquals("https://example.com/assets/icon.png", SourceIconExtractor.extract(
            "<link rel='apple-touch-icon' href='/assets/icon.png'>", "https://example.com/news/story", false))
        assertEquals("https://example.com/favicon.ico", SourceIconExtractor.favicon("https://example.com/news/"))
        assertNull(SourceIconExtractor.extract("<link rel='icon' href='javascript:bad'>", "https://example.com", false))
    }

    @Test
    fun youtubeUsesProfileImageRatherThanGenericFavicon() {
        val html = "<link rel='icon' href='/favicon.ico'><meta property='og:image' content='https://yt3.ggpht.com/profile.jpg'>"
        assertEquals("https://yt3.ggpht.com/profile.jpg", SourceIconExtractor.extract(html, "https://www.youtube.com/channel/UCabc", true))
        assertNull(SourceIconExtractor.extract("<link rel='icon' href='/favicon.ico'>", "https://youtube.com", true))
    }
}
