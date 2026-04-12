package com.mmg.manahub.feature.news.domain.model

data class ContentSource(
    val id: String,
    val name: String,
    val feedUrl: String,
    val type: SourceType,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = true,
    val iconUrl: String? = null,
    val language: String = "en", // "en", "es", "de"
)
