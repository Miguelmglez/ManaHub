package com.mmg.manahub.core.model

import com.mmg.manahub.core.model.CollectionViewMode

data class UserPreferences(
    val appLanguage: AppLanguage,
    val cardLanguage: CardLanguage,
    val newsLanguages: Set<NewsLanguage>,
    val preferredCurrency: PreferredCurrency,
    val collectionViewMode: CollectionViewMode,
)

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en-GB", "English");

    companion object {
        fun fromCode(code: String) = entries.find { it.code == code } ?: ENGLISH
    }
}

enum class CardLanguage(val code: String, val displayName: String) {
    ENGLISH("en-GB", "English"),
    SPANISH("es-ES", "Español"),
    GERMAN("de-DE", "Deutsch"),
    FRENCH("fr-FR", "Français"),
    ITALIAN("it-IT", "Italiano"),
    PORTUGUESE("pt-BR", "Português"),
    JAPANESE("ja-JP", "日本語"),
    KOREAN("ko-KR", "한국어"),
    RUSSIAN("ru-RU", "Русский"),
    CHINESE_SIMPLIFIED("zh-Hans", "简体中文"),
    CHINESE_TRADITIONAL("zh-Hant", "繁體中文");

    fun toScryfallCode(): String = when (this) {
        CHINESE_SIMPLIFIED -> "zhs"
        CHINESE_TRADITIONAL -> "zht"
        else -> code.split("-").first()
    }

    companion object {
        fun fromCode(code: String) = entries.find { it.code == code } ?: ENGLISH
    }
}

enum class NewsLanguage(val code: String, val displayName: String) {
    ENGLISH("en-GB", "English"),
    SPANISH("es-ES", "Español"),
    GERMAN("de-DE", "Deutsch");

    companion object {
        fun fromCode(code: String) = entries.find { it.code == code } ?: ENGLISH
    }
}

enum class PreferredCurrency(val code: String, val symbol: String, val displayName: String) {
    EUR("EUR", "€", "EUR (€)"),
    USD("USD", "$", "USD ($)");

    companion object {
        fun fromCode(code: String) = entries.find { it.code == code } ?: EUR
    }
}
