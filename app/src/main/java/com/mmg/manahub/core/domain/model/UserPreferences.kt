package com.mmg.manahub.core.domain.model

data class UserPreferences(
    val appLanguage: AppLanguage,
    val cardLanguage: CardLanguage,
    val newsLanguages: Set<NewsLanguage>,
    val preferredCurrency: PreferredCurrency,
)

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en-GB", "English");
    //SPANISH("es", "Español")
    //GERMAN("de", "Deutsch");

    companion object {
        fun fromCode(code: String) = entries.find { it.code == code } ?: ENGLISH
    }
}

enum class CardLanguage(val code: String, val displayName: String) {
    ENGLISH("en-GB", "English"),
    SPANISH("es-ES", "Español"),
    GERMAN("de-DE", "Deutsch");

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
