package com.mmg.manahub.feature.news.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RssFeedParser] — News feature improvements Phase 6 (dedup hardening).
 *
 * SCOPE NOTE: this class only covers [RssFeedParser.normalizeUrlForHash] / [RssFeedParser.hashUrl]
 * / [RssFeedParser.parseDate], the pure (no-Android-API) companion members. [RssFeedParser.parse]
 * and [RssFeedParser.detectChannelLanguage] — which cover the guid-vs-link dedup-identity selection
 * and the channel-language detection required by the Phase 6 / Phase 2 checklists — both call
 * `android.util.Xml.newPullParser()` internally. This project's unit tests run with
 * `testOptions.unitTests.isReturnDefaultValues = true` and have **no Robolectric dependency**, so
 * that call returns `null` under a plain JVM unit test (confirmed empirically: every test that
 * invoked `parser.parse(...)` failed with an NPE, and `detectChannelLanguage(...)` silently
 * returned null because its own `catch (_: Exception)` swallows the resulting exception). Testing
 * those two methods requires either adding a `testImplementation(libs.robolectric)` dependency (a
 * build-file change — out of this agent's scope, route through `android-kotlin-architect`) or
 * extracting the guid-selection / language-detection logic into pure functions that don't need a
 * live `XmlPullParser`. Flagged in the task summary as a suspected test-infra gap, not fixed here.
 *
 * GROUP 1 — normalizeUrlForHash: tracking-param stripping, case-folding, fragment stripping
 * GROUP 2 — hashUrl: stable/deterministic MD5-based id
 * GROUP 3 — parseDate: malformed/blank input never throws
 */
class RssFeedParserTest {

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — normalizeUrlForHash
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a URL with a utm_source param when normalized then the param is stripped`() {
        val withParam = RssFeedParser.normalizeUrlForHash("https://example.com/article?utm_source=twitter")
        val withoutParam = RssFeedParser.normalizeUrlForHash("https://example.com/article")

        assertEquals(withoutParam, withParam)
    }

    @Test
    fun `given the same article URL with different tracking-param combinations then all normalize identically`() {
        val base = RssFeedParser.normalizeUrlForHash("https://example.com/article")
        val withUtm = RssFeedParser.normalizeUrlForHash(
            "https://example.com/article?utm_source=twitter&utm_medium=social&utm_campaign=launch",
        )
        val withFbclid = RssFeedParser.normalizeUrlForHash("https://example.com/article?fbclid=abc123")
        val withGclid = RssFeedParser.normalizeUrlForHash("https://example.com/article?gclid=xyz")
        val withRef = RssFeedParser.normalizeUrlForHash("https://example.com/article?ref=homepage")

        assertEquals(base, withUtm)
        assertEquals(base, withFbclid)
        assertEquals(base, withGclid)
        assertEquals(base, withRef)
    }

    @Test
    fun `given the same URL with tracking params in different order then both normalize identically`() {
        val order1 = RssFeedParser.normalizeUrlForHash(
            "https://example.com/article?utm_source=a&utm_medium=b",
        )
        val order2 = RssFeedParser.normalizeUrlForHash(
            "https://example.com/article?utm_medium=b&utm_source=a",
        )

        assertEquals(order1, order2)
    }

    @Test
    fun `given tracking param names in different case then they are still stripped`() {
        val base = RssFeedParser.normalizeUrlForHash("https://example.com/article")
        val upperCaseParam = RssFeedParser.normalizeUrlForHash("https://example.com/article?UTM_SOURCE=twitter")

        assertEquals(base, upperCaseParam)
    }

    @Test
    fun `given a URL with a fragment then the fragment is stripped`() {
        val withFragment = RssFeedParser.normalizeUrlForHash("https://example.com/article#section-2")
        val withoutFragment = RssFeedParser.normalizeUrlForHash("https://example.com/article")

        assertEquals(withoutFragment, withFragment)
    }

    @Test
    fun `given a URL with mixed-case scheme and host then both are lowercased`() {
        val mixedCase = RssFeedParser.normalizeUrlForHash("HTTPS://Example.COM/article")
        val lowerCase = RssFeedParser.normalizeUrlForHash("https://example.com/article")

        assertEquals(lowerCase, mixedCase)
    }

    @Test
    fun `given a URL with mixed-case path then the path case is preserved (not lowercased)`() {
        val normalized = RssFeedParser.normalizeUrlForHash("https://example.com/Article-Title")

        assertTrue(
            "path casing must be preserved, only scheme+host are lowercased",
            normalized.contains("/Article-Title"),
        )
    }

    @Test
    fun `given a URL with a non-tracking query param then it is preserved as-is`() {
        val normalized = RssFeedParser.normalizeUrlForHash("https://example.com/article?id=42")

        assertTrue("non-tracking query params must be preserved", normalized.contains("id=42"))
    }

    @Test
    fun `given a URL mixing tracking and non-tracking params then only the tracking ones are stripped`() {
        val normalized = RssFeedParser.normalizeUrlForHash(
            "https://example.com/article?id=42&utm_source=twitter",
        )

        assertTrue("non-tracking param must survive", normalized.contains("id=42"))
        assertTrue("tracking param must be stripped", !normalized.contains("utm_source"))
    }

    @Test
    fun `given a malformed URL then normalizeUrlForHash falls back to the trimmed original string`() {
        val malformed = "not a url at all ::: with spaces"
        val normalized = RssFeedParser.normalizeUrlForHash(malformed)

        assertEquals(malformed.trim(), normalized)
    }

    @Test
    fun `given a URL with surrounding whitespace then it is trimmed`() {
        val normalized = RssFeedParser.normalizeUrlForHash("  https://example.com/article  ")

        assertEquals("https://example.com/article", normalized)
    }

    @Test
    fun `given a port in the URL then it is preserved`() {
        val normalized = RssFeedParser.normalizeUrlForHash("https://example.com:8443/article")

        assertTrue("explicit port must be preserved", normalized.contains(":8443"))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — hashUrl
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given the same input string when hashed twice then the result is identical (deterministic)`() {
        val url = "https://example.com/article"

        assertEquals(RssFeedParser.hashUrl(url), RssFeedParser.hashUrl(url))
    }

    @Test
    fun `given two different URLs then their hashes differ`() {
        val hash1 = RssFeedParser.hashUrl("https://example.com/article-one")
        val hash2 = RssFeedParser.hashUrl("https://example.com/article-two")

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `given a URL with vs without tracking params then the end-to-end hash is identical`() {
        val plain = RssFeedParser.hashUrl(RssFeedParser.normalizeUrlForHash("https://example.com/article"))
        val tracked = RssFeedParser.hashUrl(
            RssFeedParser.normalizeUrlForHash("https://example.com/article?utm_source=twitter&utm_medium=social"),
        )

        assertEquals("dedup id must match regardless of tracking params", plain, tracked)
    }

    @Test
    fun `given a URL with a tracking param vs a genuinely different article then the hashes differ`() {
        val article1 = RssFeedParser.hashUrl(
            RssFeedParser.normalizeUrlForHash("https://example.com/article-one?utm_source=twitter"),
        )
        val article2 = RssFeedParser.hashUrl(
            RssFeedParser.normalizeUrlForHash("https://example.com/article-two?utm_source=twitter"),
        )

        assertNotEquals(
            "normalization must never collapse genuinely different articles to the same id",
            article1,
            article2,
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — parseDate
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given an unparseable pubDate then parseDate returns 0L instead of throwing`() {
        assertEquals(0L, RssFeedParser.parseDate("not a date"))
    }

    @Test
    fun `given a blank pubDate then parseDate returns 0L`() {
        assertEquals(0L, RssFeedParser.parseDate(""))
        assertEquals(0L, RssFeedParser.parseDate("   "))
    }

    @Test
    fun `given a valid RFC-822 pubDate then parseDate returns a positive epoch millis`() {
        val millis = RssFeedParser.parseDate("Mon, 01 Jan 2024 12:00:00 GMT")

        assertTrue("a well-formed RFC-822 date must parse to a positive epoch", millis > 0L)
    }

    @Test
    fun `given a valid ISO-8601 pubDate then parseDate returns a positive epoch millis`() {
        val millis = RssFeedParser.parseDate("2024-01-01T12:00:00Z")

        assertTrue("a well-formed ISO-8601 date must parse to a positive epoch", millis > 0L)
    }
}
