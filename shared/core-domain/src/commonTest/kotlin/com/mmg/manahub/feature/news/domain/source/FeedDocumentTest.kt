package com.mmg.manahub.feature.news.domain.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedDocumentTest {

    private val rss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!-- generator -->
        <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
        <channel>
          <title><![CDATA[Hipsters &amp; Friends]]></title>
          <atom:link href="https://example.com/feed/" rel="self" type="application/rss+xml"/>
          <link>https://example.com</link>
          <language>es-ES</language>
          <item><title>Item title</title><link>https://example.com/post</link><language>de</language></item>
        </channel></rss>
    """.trimIndent()

    private val youTubeAtom = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns="http://www.w3.org/2005/Atom">
         <link rel="self" href="http://www.youtube.com/feeds/videos.xml?channel_id=UC8ZGymAvfP97qJabgqUkz4A"/>
         <title>Magic: The Gathering</title>
         <link rel="alternate" href="https://www.youtube.com/channel/UC8ZGymAvfP97qJabgqUkz4A"/>
         <entry><title>Video</title><link rel="alternate" href="https://www.youtube.com/watch?v=x"/></entry>
        </feed>
    """.trimIndent()

    @Test
    fun given_rssDocument_then_itLooksLikeAFeed() {
        assertTrue(FeedDocument.looksLikeFeed(rss))
        assertTrue(FeedDocument.looksLikeFeed(youTubeAtom))
        assertTrue(FeedDocument.looksLikeFeed("﻿<rdf:RDF xmlns:rdf=\"x\"></rdf:RDF>"))
    }

    @Test
    fun given_htmlPage_then_itIsNotAFeed() {
        assertFalse(FeedDocument.looksLikeFeed("<!DOCTYPE html><html><head><title>Site</title></head></html>"))
        assertNull(FeedDocument.parseChannelMeta("<html><title>x</title></html>"))
    }

    @Test
    fun given_rssChannel_then_titleLinkAndLanguageComeFromTheChannelNotAnItem() {
        val meta = FeedDocument.parseChannelMeta(rss)

        assertEquals(FeedChannelMeta(title = "Hipsters & Friends", link = "https://example.com", language = "es"), meta)
    }

    @Test
    fun given_youTubeAtomFeed_then_titleAndAlternateLinkAreRead() {
        val meta = FeedDocument.parseChannelMeta(youTubeAtom)

        assertEquals("Magic: The Gathering", meta?.title)
        assertEquals("https://www.youtube.com/channel/UC8ZGymAvfP97qJabgqUkz4A", meta?.link)
        assertNull(meta?.language)
    }

    @Test
    fun given_atomXmlLang_then_itIsUsedAsTheLanguage() {
        val atom = """<feed xmlns="http://www.w3.org/2005/Atom" xml:lang="de-DE"><title>T</title></feed>"""

        assertEquals("de", FeedDocument.parseChannelMeta(atom)?.language)
    }

    @Test
    fun given_unsupportedLanguage_then_itMapsToNull() {
        assertNull(FeedDocument.supportedLanguage("fr-FR"))
        assertEquals("en", FeedDocument.supportedLanguage(" EN-us "))
        assertNull(FeedDocument.supportedLanguage(null))
    }
}
