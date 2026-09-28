package com.mmg.manahub.core.data.tagging

import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.model.TagDictionaryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TagDictionaryRemoteCatalogTest {
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
