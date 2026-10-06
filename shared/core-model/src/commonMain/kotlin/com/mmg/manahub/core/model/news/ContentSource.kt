package com.mmg.manahub.core.model.news

/** A news feed source; [isEnabled] means "followed" (the feed only shows followed sources). */
data class ContentSource(
    val id: String,
    val name: String,
    val feedUrl: String,
    val type: SourceType,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = true,
    val iconUrl: String? = null,
    val language: String = "en",
    val siteUrl: String? = null,
    val lastFetchedAt: Long = 0L,
) {
    companion object {
        /** Short language codes the News feature understands (matches [language]). */
        val SUPPORTED_LANGUAGES = listOf("en", "es", "de")
    }
}
