package com.mmg.manahub.feature.news.domain.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedAutodiscoveryTest {

    private val page = "https://www.example.com/blog/index.html"

    @Test
    fun given_rssAndAtomAlternates_then_bothAreFoundInDocumentOrder() {
        val html = """
            <link rel="alternate" type="application/rss+xml" title="Feed" href="https://www.example.com/feed/">
            <link type='application/atom+xml' href='/atom.xml' rel='alternate'>
            <link rel="stylesheet" href="/style.css">
        """.trimIndent()

        assertEquals(
            listOf("https://www.example.com/feed/", "https://www.example.com/atom.xml"),
            FeedAutodiscovery.discover(html, page),
        )
    }

    @Test
    fun given_relativeAndProtocolRelativeHrefs_then_theyResolveAgainstThePage() {
        val html = """
            <link rel="alternate" type="application/rss+xml" href="rss.xml">
            <link rel="alternate" type="application/rss+xml" href="//cdn.example.com/feed">
        """.trimIndent()

        assertEquals(
            listOf("https://www.example.com/blog/rss.xml", "https://cdn.example.com/feed"),
            FeedAutodiscovery.discover(html, page),
        )
    }

    @Test
    fun given_httpHref_then_itIsUpgradedToHttps() {
        val html = """<link rel="alternate" type="application/rss+xml" href="http://example.com/feed">"""

        assertEquals(listOf("https://example.com/feed"), FeedAutodiscovery.discover(html, page))
    }

    @Test
    fun given_commentsFeedListedFirst_then_theMainFeedComesFirst() {
        val html = """
            <link rel="alternate" type="application/rss+xml" href="https://example.com/comments/feed/">
            <link rel="alternate" type="application/rss+xml" href="https://example.com/feed/">
        """.trimIndent()

        assertEquals("https://example.com/feed/", FeedAutodiscovery.discover(html, page).first())
    }

    @Test
    fun given_nonFeedAlternatesAndOddSchemes_then_theyAreIgnored() {
        val html = """
            <link rel="alternate" hreflang="es" href="https://example.com/es/">
            <link rel="alternate" type="application/rss+xml" href="javascript:alert(1)">
            <link rel="alternate" type="application/json+oembed" href="https://example.com/oembed">
        """.trimIndent()

        assertTrue(FeedAutodiscovery.discover(html, page).isEmpty())
    }

    @Test
    fun given_encodedAmpersandInHref_then_itIsDecoded() {
        val html = """<link rel="alternate" type="application/rss+xml" href="/feed?a=1&amp;b=2">"""

        assertEquals(listOf("https://www.example.com/feed?a=1&b=2"), FeedAutodiscovery.discover(html, page))
    }

    @Test
    fun given_fallbackPaths_then_theyAreBuiltOnThePageOrigin() {
        assertEquals(
            FeedAutodiscovery.FALLBACK_FEED_PATHS.map { "https://www.example.com$it" },
            FeedAutodiscovery.fallbackFeedUrls(page),
        )
        assertEquals(6, FeedAutodiscovery.FALLBACK_FEED_PATHS.size)
    }
}
