package com.mmg.manahub.core.util

object CardConstants {
    /** Exact character count of a valid game tag (format: #XXXXXX). */
    const val GAME_TAG_LENGTH = 7

    /**
     * Card conditions as `code to English display name` pairs.
     *
     * The util layer is resource-free (KMP modularization, Phase 0.5 Blocker 5) so display
     * names are plain English strings rather than Android string-resource ids; the app is
     * English-only.
     */
    val conditions = listOf(
        "M" to "Mint",
        "NM" to "Near Mint",
        "EX" to "Excellent",
        "GD" to "Good",
        "LP" to "Light Played",
        "PL" to "Played",
        "PO" to "Poor",
    )

    val languages = listOf(
        "en" to "🇬🇧",
        "es" to "🇪🇸",
        "de" to "🇩🇪",
        "fr" to "🇫🇷",
        "it" to "🇮🇹",
        "pt" to "🇵🇹",
        "ja" to "🇯🇵",
        "ko" to "🇰🇷",
        "ru" to "🇷🇺",
        "zhs" to "🇨🇳",
        "zht" to "🇹🇼",
    )

    val languageNames = mapOf(
        "en" to "English",
        "es" to "Spanish",
        "de" to "German",
        "fr" to "French",
        "it" to "Italian",
        "pt" to "Portuguese",
        "ja" to "Japanese",
        "ko" to "Korean",
        "ru" to "Russian",
        "zhs" to "Chinese Simplified",
        "zht" to "Chinese Traditional",
    )

    fun getFlag(langCode: String): String {
        return languages.find { it.first == langCode }?.second ?: ""
    }

    fun getLanguageName(langCode: String): String {
        return languageNames[langCode] ?: langCode.uppercase()
    }

    fun getConditionName(code: String): String {
        return conditions.find { it.first == code }?.second ?: "Near Mint"
    }
}
