package com.mmg.manahub.core.data.tagging

import com.mmg.manahub.core.model.CardTypeOption
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.model.TagDictionaryEntry
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.DeckIdentitySeedTags
import com.mmg.manahub.feature.decks.domain.engine.PostureId
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Guards parity with dynamic type-line tags and the persisted deck identity vocabulary. */
class TagDictionaryManualCatalogTest {
    @Test fun manualTypesUseAnalyzerTokenIdentitiesWithoutChangingDetectionInventory() {
        val detectionKeys = TagDictionary.all().map { it.key }.toSet()
        val entries = TagDictionary.systemCatalogEntries().associateBy { it.key }
        for (key in listOf("sloth", "druid", "warrior", "basic_land", "time", "lord", "assembly", "worker", "power", "plant")) {
            assertNotNull(entries[key], "Missing type-line token: $key")
        }
        assertEquals(TagCategory.TYPE, entries.getValue("druid").category)
        assertEquals(TagCategory.TRIBAL, entries.getValue("elf").category)
        assertEquals("Basic Land", entries.getValue("basic_land").labels["en"])
        assertFalse("time_lord" in entries)
        assertFalse("assembly_worker" in entries)
        assertEquals(detectionKeys, TagDictionary.all().map { it.key }.toSet())
        for (option in CardTypeOption.allTypes) {
            val keys = option.scryfallValue.replace("—", " ").replace("-", " ").replace("//", " ")
                .split(' ').map { it.trim().lowercase() }.filter { it.length > 1 }
            assertTrue(entries.keys.containsAll(keys), "Missing vocabulary for ${option.scryfallValue}")
        }
    }

    @Test fun supplementalTypesPreserveRemoteMetadataAndExcludeUserOverrides() {
        val remote = TagDictionaryEntry("druid", TagCategory.KEYWORD, mapOf("en" to "Remote Druid"), emptyList())
        try {
            TagDictionary.applyRemoteEntries(listOf(remote))
            TagDictionary.applyOverrides(listOf(TagOverride("custom_private", labels = mapOf("en" to "Private"), category = TagCategory.CUSTOM)))
            val entries = TagDictionary.systemCatalogEntries().associateBy { it.key }
            assertEquals(remote, entries["druid"])
            assertFalse("custom_private" in entries)
            assertTrue(entries.getValue("warrior").rules.isEmpty())
        } finally {
            TagDictionary.applyRemoteEntries(emptyList())
            TagDictionary.applyOverrides(emptyList())
        }
    }

    @Test fun everyPersistedDeckIdentitySeedIsAvailableForManualSelection() {
        val entries = TagDictionary.systemCatalogEntries().associateBy { it.key }
        val seeds = ArchetypeId.entries.flatMap(DeckIdentitySeedTags::archetypeSeedTags) +
            PostureId.entries.flatMap(DeckIdentitySeedTags::postureSeedTags) +
            DeckIdentitySeedTags.themeSeedTags(ThemeId.entries)
        seeds.forEach { assertNotNull(entries[it.key], "Missing deck identity seed: ${it.key}") }
        assertEquals(TagCategory.STRATEGY, entries.getValue("attrition").category)
        assertTrue(entries.getValue("attrition").rules.isEmpty())
        assertFalse(entries.keys.any { it.startsWith("tribe:") })
    }
}
