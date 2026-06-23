package com.mmg.manahub.core.model.news

/**
 * A news / content feed source configuration. Moved from `:app` feature/news/domain/model
 * during the KMP migration so that the [NewsRepository] interface (`:shared:core-domain`)
 * can reference it from `commonMain`.
 */
data class ContentSource(
    val id: String,
    val name: String,
    val feedUrl: String,
    val type: SourceType,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = true,
    val iconUrl: String? = null,
    val language: String = "en",
)
