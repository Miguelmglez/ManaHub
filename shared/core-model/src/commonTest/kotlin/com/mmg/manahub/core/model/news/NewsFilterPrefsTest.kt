package com.mmg.manahub.core.model.news

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [NewsFilterPrefs] — News feature improvements Phase 2 (language model).
 *
 * [NewsFilterPrefs.SUPPORTED_NEWS_LANGUAGES] is documented as the single source of truth the
 * filter-sheet language chips and the add-source language selector both derive their options
 * from. The actual chip-rendering / "select all" wiring lives in Compose UI
 * (`NewsFilterSheet.kt`, `NewsSourcesSettingsScreen.kt`) which is out of scope for a Kotlin unit
 * test — this class covers the one piece of that logic that IS a plain, testable Kotlin value.
 */
class NewsFilterPrefsTest {

    @Test
    fun given_supportedNewsLanguages_then_itIsExactlyEnglishSpanishGerman() {
        assertEquals(listOf("en", "es", "de"), NewsFilterPrefs.SUPPORTED_NEWS_LANGUAGES)
    }

    @Test
    fun given_theDefaultFilterPrefs_then_languagesIsEnglishOnly() {
        assertEquals(setOf("en"), NewsFilterPrefs.DEFAULT.languages)
    }

    @Test
    fun given_theDefaultFilterPrefs_then_bothContentTypesAreIncluded() {
        assertEquals(setOf(SourceType.ARTICLE, SourceType.VIDEO), NewsFilterPrefs.DEFAULT.types)
    }

    @Test
    fun given_theDefaultFilterPrefs_then_sourceIdsIsNull_meaningAllEnabledSources() {
        assertEquals(null, NewsFilterPrefs.DEFAULT.sourceIds)
    }
}
