package com.mmg.manahub.core.data.tagging

import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.model.TagDictionaryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagDictionaryRemoteCatalogTest {
    @Test
    fun systemCatalogPreservesBaseAndRemoteMetadataWithoutUserCreatedEntries() {
        val baseKeys = TagDictionary.systemCatalogEntries().map { it.key }.toSet()
        val remote = TagDictionaryEntry("registered_mechanic", TagCategory.KEYWORD, mapOf("en" to "Registered Mechanic"), rules = emptyList())
        try {
            TagDictionary.applyRemoteEntries(listOf(remote))
            TagDictionary.applyOverrides(listOf(
                TagOverride("private_unprefixed", labels = mapOf("en" to "Private tag"), category = TagCategory.ROLE),
                TagOverride("custom_sample", labels = mapOf("en" to "Private custom tag"), category = TagCategory.CUSTOM),
            ))
            val entries = TagDictionary.systemCatalogEntries().associateBy { it.key }
            assertTrue(entries.keys.containsAll(baseKeys))
            assertEquals(remote, entries[remote.key])
            assertFalse("private_unprefixed" in entries)
            assertFalse("custom_sample" in entries)
        } finally {
            TagDictionary.applyRemoteEntries(emptyList())
            TagDictionary.applyOverrides(emptyList())
        }
    }

    @Test
    fun metadataOnlyRemoteEntryKeepsBaseRulesAndUserOverrides() {
        val original = assertNotNull(TagDictionary.get("plus_counters"))
        val userTag = TagOverride(
            key = "custom_sample",
            category = TagCategory.CUSTOM,
            labels = mapOf("en" to "My tag"),
        )

        try {
            TagDictionary.applyOverrides(listOf(userTag))
            TagDictionary.applyRemoteEntries(
                listOf(
                    TagDictionaryEntry(
                        key = original.key,
                        category = original.category,
                        labels = mapOf("en" to "Counters"),
                        rules = emptyList(),
                    ),
                ),
            )

            assertEquals(original.rules, TagDictionary.get(original.key)?.rules)
            assertEquals("Counters", TagDictionary.get(original.key)?.labels?.get("en"))
            assertEquals("My tag", TagDictionary.get(userTag.key)?.labels?.get("en"))
        } finally {
            TagDictionary.applyRemoteEntries(emptyList())
            TagDictionary.applyOverrides(emptyList())
        }
    }
}
