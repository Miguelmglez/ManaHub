package com.mmg.manahub.feature.news.domain.source

import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Site URL derivation, feed identity, default follow policy and the legacy follow migration. */
class SourceHelpersTest {

    private val youTubeFeed = "https://www.youtube.com/feeds/videos.xml?channel_id=UC8ZGymAvfP97qJabgqUkz4A"

    @Test
    fun given_youTubeFeed_then_siteUrlIsTheChannelPage() {
        assertEquals(
            "https://www.youtube.com/channel/UC8ZGymAvfP97qJabgqUkz4A",
            SiteUrlDerivation.derive(youTubeFeed, channelLink = "https://example.com"),
        )
    }

    @Test
    fun given_articleFeedWithChannelLink_then_siteUrlIsTheHttpsLink() {
        assertEquals("https://example.com/", SiteUrlDerivation.derive("https://example.com/feed", "http://example.com/"))
    }

    @Test
    fun given_articleFeedWithoutUsableLink_then_siteUrlIsTheFeedOrigin() {
        assertEquals("https://blog.example.com", SiteUrlDerivation.derive("https://blog.example.com/feed", null))
        assertEquals("https://blog.example.com", SiteUrlDerivation.derive("https://blog.example.com/feed", "mailto:x@y.z"))
        assertNull(SiteUrlDerivation.httpsLink("not a url"))
    }

    @Test
    fun given_equivalentFeedUrls_then_theyShareOneIdentity() {
        assertEquals(FeedIdentity.of("https://www.Example.com/feed/"), FeedIdentity.of("http://example.com/feed"))
        assertEquals(FeedIdentity.of(youTubeFeed), FeedIdentity.of("https://youtube.com/feeds/videos.xml?channel_id=UC8ZGymAvfP97qJabgqUkz4A"))
        assertFalse(FeedIdentity.of("https://example.com/feed") == FeedIdentity.of("https://example.com/rss"))
    }

    @Test
    fun given_defaultFollowPolicy_then_englishAndTheDeviceLanguageAreFollowed() {
        assertTrue(DefaultFollowPolicy.isFollowedByDefault("en", "de"))
        assertTrue(DefaultFollowPolicy.isFollowedByDefault("es", "ES"))
        assertFalse(DefaultFollowPolicy.isFollowedByDefault("de", "es"))
    }

    private fun source(id: String, language: String, enabled: Boolean = true) =
        ContentSource(id = id, name = id, feedUrl = "https://$id.com/feed", type = SourceType.ARTICLE, isEnabled = enabled, language = language)

    private val sources = listOf(
        source("en1", "en"),
        source("en2", "en"),
        source("es1", "es"),
        source("de1", "de"),
        source("off", "en", enabled = false),
    )

    @Test
    fun given_legacyEnglishOnly_then_onlyEnabledEnglishSourcesStayFollowed() {
        val result = LegacyFollowMigration.computeFollowedIds(sources, setOf("en"), legacyAllowlist = null, legacyExplicitEmpty = false)

        assertEquals(setOf("en1", "en2"), result)
    }

    @Test
    fun given_legacyAllowlist_then_itNarrowsTheLanguageMatch() {
        val result = LegacyFollowMigration.computeFollowedIds(sources, setOf("en", "es"), setOf("en2", "es1", "off"), false)

        assertEquals(setOf("en2", "es1"), result)
    }

    @Test
    fun given_explicitEmptyAllowlist_then_onlyTheLanguageRuleApplies() {
        val result = LegacyFollowMigration.computeFollowedIds(sources, setOf("es"), legacyAllowlist = null, legacyExplicitEmpty = true)

        assertEquals(setOf("es1"), result)
    }

    @Test
    fun given_allowlistMatchingNothing_then_theLanguageRuleIsUsedInstead() {
        val result = LegacyFollowMigration.computeFollowedIds(sources, setOf("de"), setOf("deleted-custom"), false)

        assertEquals(setOf("de1"), result)
    }

    @Test
    fun given_noLanguageMatch_then_theCurrentFollowedSetIsKept() {
        val result = LegacyFollowMigration.computeFollowedIds(sources, setOf("fr"), null, false)

        assertEquals(setOf("en1", "en2", "es1", "de1"), result)
    }

    @Test
    fun given_noSources_then_nothingIsFollowed() {
        assertTrue(LegacyFollowMigration.computeFollowedIds(emptyList(), setOf("en"), null, false).isEmpty())
    }
}
