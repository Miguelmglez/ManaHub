package com.mmg.manahub.core.model.news

/** A feed found from pasted input, previewed before following; [language] is null when undetected. */
data class ResolvedSource(
    val name: String,
    val feedUrl: String,
    val siteUrl: String?,
    val type: SourceType,
    val language: String?,
    val preview: List<NewsItem>,
)
