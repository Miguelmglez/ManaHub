package com.mmg.manahub.core.tagging

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.model.SuggestedTag
import com.mmg.manahub.core.domain.model.TagCategory

// ═══════════════════════════════════════════════════════════════════════════════
//  Three independent analyzers feed [SuggestTagsUseCase].
//
//  Each returns a list of [SuggestedTag] with a confidence in [0,1]. The
//  use case dedupes by key (keeping the highest confidence) and splits the
//  results into "auto-add" vs "ask the user" buckets according to the
//  configurable thresholds.
// ═══════════════════════════════════════════════════════════════════════════════

object KeywordAnalyzer {
    /** Each Scryfall keyword becomes a tag with confidence = 1.0. */
    fun analyze(card: Card): List<SuggestedTag> = card.keywords.map { raw ->
        val key = raw.trim().lowercase().replace(' ', '_').replace("//", " ").replace("-", "_")
        SuggestedTag(
            tag        = CardTag(key, TagCategory.KEYWORD),
            confidence = 1.0f,
            source     = "keyword",
        )
    }
}

object TypeLineAnalyzer {
    /**
     * Tokenizes the (English) [Card.typeLine] and emits one tag per word.
     *
     * "Legendary Creature — Elf Druid"  →  legendary, creature, elf, druid
     *
     * Tags emitted with [TagCategory.TYPE] use [TagDictionary]'s delegation to
     * [com.mmg.manahub.core.util.CardTypeTranslator] for localization.
     * Confidence = 1.0 (Scryfall's type_line is authoritative).
     */
    fun analyze(card: Card): List<SuggestedTag> {
        val out = mutableListOf<SuggestedTag>()
        var typeLine = card.typeLine
            .replace("—", " ")
            .replace("-", " ")
            .replace("//", " ")

        // Detect and consume "Basic Land" (including snow-covered) as a single combined tag.
        val basicLandPattern = Regex("Basic (Snow )?Land", RegexOption.IGNORE_CASE)
        if (basicLandPattern.containsMatchIn(typeLine)) {
            out += SuggestedTag(
                tag        = CardTag("basic_land", TagCategory.TYPE),
                confidence = 1.0f,
                source     = "type_line",
            )
            typeLine = typeLine.replace(basicLandPattern, "")
        }

        val parts = typeLine
            .split(' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length > 1 }

        parts.forEach { token ->
            val key = token.lowercase()
            // Promote to canonical tribal/strategy tag when one exists.
            val canonical = CardTag.canonical.firstOrNull { it.key == key }
            val tag = canonical ?: CardTag(key, TagCategory.TYPE)
            out += SuggestedTag(tag = tag, confidence = 1.0f, source = "type_line")
        }
        return out
    }
}

object GameChangerAnalyzer {
    /** Emits a "Game Changer" tag with full confidence when the card carries the Scryfall flag. */
    fun analyze(card: Card): List<SuggestedTag> {
        if (!card.gameChanger) return emptyList()
        return listOf(
            SuggestedTag(
                tag        = CardTag.GAME_CHANGER,
                confidence = 1.0f,
                source     = "scryfall",
            )
        )
    }
}

object StrategyAnalyzer {

    /**
     * Scryfall oracle text embeds parenthesized reminder text (e.g. deathtouch's
     * "(Any amount of damage this deals to a creature is enough to destroy it.)").
     * Reminder text is a false-positive source for substring matching, so we strip
     * it before analysis. Precompiled once — this runs on the @DefaultDispatcher
     * for whole-collection refreshes.
     */
    private val reminderTextRegex = Regex("\\([^)]*\\)")

    /**
     * Scans the English [Card.oracleText] against [TagDictionary] detection rules.
     *
     * The text is normalized once per card (lowercased → reminder text stripped →
     * the card's own name(s) replaced with "~" so self-referential templating can
     * be matched). Each entry whose rules match contributes a confidence equal to
     * the highest matched-rule confidence, +0.03 per additional matched rule,
     * coerced to ≤ 0.99 (multi-evidence bump).
     */
    fun analyze(card: Card): List<SuggestedTag> {
        val oracle = card.oracleText?.takeIf { it.isNotBlank() } ?: return emptyList()

        val text = normalize(oracle, card.name)
        val typeLineLower = card.typeLine.lowercase()

        val results = mutableListOf<SuggestedTag>()

        TagDictionary.all().forEach { entry ->
            if (entry.rules.isEmpty() || entry.baseConfidence <= 0f) return@forEach

            var matchedCount = 0
            var maxConfidence = 0f
            entry.rules.forEach { rule ->
                if (matches(rule, text, typeLineLower)) {
                    matchedCount++
                    val ruleConfidence = rule.confidence ?: entry.baseConfidence
                    if (ruleConfidence > maxConfidence) maxConfidence = ruleConfidence
                }
            }

            if (matchedCount > 0) {
                val confidence = (maxConfidence + 0.03f * (matchedCount - 1)).coerceIn(0f, 0.99f)
                results += SuggestedTag(
                    tag        = CardTag(entry.key, entry.category),
                    confidence = confidence,
                    source     = "strategy",
                )
            }
        }

        return results
    }

    /**
     * Lowercase → strip parenthesized reminder text → replace the card's own
     * name (and each face name for split/MDFC cards joined by " // ") with "~".
     */
    private fun normalize(oracleText: String, cardName: String): String {
        var text = oracleText.lowercase()
        text = reminderTextRegex.replace(text, "")
        val names = cardName.split(" // ")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .sortedByDescending { it.length } // replace the longest face first
        names.forEach { name -> text = text.replace(name, "~") }
        return text
    }

    /** True when [rule] matches the normalized [text] and lowercase [typeLine]. */
    private fun matches(rule: DetectionRule, text: String, typeLine: String): Boolean {
        if (rule.allOf.any { !text.contains(it) }) return false
        if (rule.anyOf.isNotEmpty() && rule.anyOf.none { text.contains(it) }) return false
        if (rule.noneOf.any { text.contains(it) }) return false
        if (rule.typeLineAnyOf.isNotEmpty() && rule.typeLineAnyOf.none { typeLine.contains(it) }) return false
        if (rule.typeLineNoneOf.any { typeLine.contains(it) }) return false
        return true
    }
}
