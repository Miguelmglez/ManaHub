package com.mmg.manahub.core.util

import com.mmg.manahub.R

object CardConstants {
    /** Exact character count of a valid game tag (format: #XXXXXX). */
    const val GAME_TAG_LENGTH = 7

    val conditions = listOf(
        "M" to R.string.card_condition_m,
        "NM" to R.string.card_condition_nm,
        "EX" to R.string.card_condition_ex,
        "GD" to R.string.card_condition_gd,
        "LP" to R.string.card_condition_lp,
        "PL" to R.string.card_condition_pl,
        "PO" to R.string.card_condition_p
    )

    val languages = listOf(
        "en" to "🇺🇸",
        "es" to "🇪🇸",
        "de" to "🇩🇪",
        "fr" to "🇫🇷",
        "it" to "🇮🇹",
        "pt" to "🇵🇹",
        "ja" to "🇯🇵",
        "ko" to "🇰🇷",
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
    )

    fun getFlag(langCode: String): String {
        return languages.find { it.first == langCode }?.second ?: ""
    }

    fun getLanguageName(langCode: String): String {
        return languageNames[langCode] ?: langCode.uppercase()
    }

    fun getConditionName(code: String): Int {
        return conditions.find { it.first == code }?.second ?: R.string.card_condition_nm
    }
}
