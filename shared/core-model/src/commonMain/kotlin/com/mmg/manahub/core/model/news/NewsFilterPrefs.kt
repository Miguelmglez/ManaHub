package com.mmg.manahub.core.model.news

import com.mmg.manahub.core.model.news.NewsFilterPrefs.Companion.DEFAULT


/**
 * Persisted News feed filter selection — the single source of truth shared by the
 * full News screen and the Home dashboard news widget.
 *
 * Persisted in `UserPreferencesDataStore`. The defaults are English-only ([DEFAULT]):
 * both content [types], every enabled source ([sourceIds] == null), and just the
 * English language.
 *
 * @param languages short language codes (`"en"`, `"es"`, `"de"`) — matches
 *  `ContentSource.language`, NOT the long `NewsLanguage.code` values.
 * @param types the [SourceType]s to include (article and/or video).
 * @param sourceIds explicit allowlist of source ids, or null when "all enabled
 *  sources" applies (the common case).
 */
data class NewsFilterPrefs(
    val languages: Set<String>,
    val types: Set<SourceType>,
    val sourceIds: Set<String>?,
) {
    companion object {
        /** Default filter selection: English-only, both content types, all enabled sources. */
        val DEFAULT = NewsFilterPrefs(
            languages = setOf("en"),
            types = setOf(SourceType.ARTICLE, SourceType.VIDEO),
            sourceIds = null,
        )
    }
}
