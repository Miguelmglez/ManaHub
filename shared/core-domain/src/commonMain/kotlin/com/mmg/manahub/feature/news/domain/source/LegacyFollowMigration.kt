package com.mmg.manahub.feature.news.domain.source

import com.mmg.manahub.core.model.news.ContentSource

/** One-time translation of the retired language/source filters into the follow model. */
object LegacyFollowMigration {

    /** Keeps followed sources matching the legacy language + allowlist; an empty result falls back to language only, then to no change. */
    fun computeFollowedIds(
        sources: List<ContentSource>,
        legacyLanguages: Set<String>,
        legacyAllowlist: Set<String>?,
        legacyExplicitEmpty: Boolean,
    ): Set<String> {
        val followed = sources.filter { it.isEnabled }
        val allowlist = if (legacyExplicitEmpty) emptySet() else legacyAllowlist
        val byLanguage = followed.filter { it.language in legacyLanguages }
        val byLanguageAndAllowlist = byLanguage.filter { allowlist == null || it.id in allowlist }
        return when {
            byLanguageAndAllowlist.isNotEmpty() -> byLanguageAndAllowlist
            byLanguage.isNotEmpty() -> byLanguage
            else -> followed
        }.map { it.id }.toSet()
    }
}
