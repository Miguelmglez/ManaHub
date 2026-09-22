package com.mmg.manahub.core.domain.collection.transfer

import com.mmg.manahub.core.util.CardConstants

/**
 * Maps third-party condition/language spellings onto the app's codes (`CardConstants`) and back.
 * Unknown input falls back to [DEFAULT_CONDITION] / [DEFAULT_LANGUAGE].
 */
object CollectionCardAttributes {

    const val DEFAULT_CONDITION = "NM"
    const val DEFAULT_LANGUAGE = "en"

    private val conditionCodes = CardConstants.conditions.map { it.first }.toSet()
    private val languageCodes = CardConstants.languageNames.keys

    private val conditionAliases = mapOf(
        "mint" to "M",
        "nearmint" to "NM",
        "excellent" to "EX",
        "good" to "GD",
        "goodlightlyplayed" to "LP",
        "lightlyplayed" to "LP",
        "lightplayed" to "LP",
        "played" to "PL",
        "moderatelyplayed" to "PL",
        "heavilyplayed" to "PL",
        "poor" to "PO",
        "damaged" to "PO",
    )

    private val languageAliases: Map<String, String> =
        CardConstants.languageNames.entries.associate { (code, name) -> normalizeKey(name) to code } +
            mapOf(
                "chinesesimplified" to "zhs",
                "simplifiedchinese" to "zhs",
                "chinesetraditional" to "zht",
                "traditionalchinese" to "zht",
                "zhcn" to "zhs",
                "zhtw" to "zht",
                "jp" to "ja",
                "kr" to "ko",
            )

    /** App condition code for [raw], or [DEFAULT_CONDITION]. */
    fun conditionCode(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return DEFAULT_CONDITION
        val upper = value.uppercase()
        if (upper in conditionCodes) return upper
        return conditionAliases[normalizeKey(value)] ?: DEFAULT_CONDITION
    }

    /** App language code for [raw] (a code or an English name), or [DEFAULT_LANGUAGE]. */
    fun languageCode(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return DEFAULT_LANGUAGE
        val lower = value.lowercase()
        if (lower in languageCodes) return lower
        return languageAliases[normalizeKey(value)] ?: DEFAULT_LANGUAGE
    }

    // Moxfield's scale is TCGplayer-style: EX and GD have no own step and collapse to Lightly Played.
    fun moxfieldCondition(code: String): String = when (code.uppercase()) {
        "M" -> "Mint"
        "NM" -> "Near Mint"
        "EX", "GD", "LP" -> "Lightly Played"
        "PL" -> "Moderately Played"
        "PO" -> "Damaged"
        else -> "Near Mint"
    }

    /** ManaBox spells the Cardmarket scale in snake_case. */
    fun manaBoxCondition(code: String): String = when (code.uppercase()) {
        "M" -> "mint"
        "EX" -> "excellent"
        "GD" -> "good"
        "LP" -> "light_played"
        "PL" -> "played"
        "PO" -> "poor"
        else -> "near_mint"
    }

    /** English language name used by Moxfield. */
    fun moxfieldLanguage(code: String): String =
        CardConstants.languageNames[code.lowercase()] ?: "English"

    private fun normalizeKey(value: String): String = value.lowercase().filter { it.isLetter() }
}
