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
     * Scans oracle / printed text against [TagDictionary] patterns in EN, ES, DE.
     * Each entry contributes its `baseConfidence` (with a small bump per extra
     * matched pattern) when at least one of its language pattern groups hits.
     */
    fun analyze(card: Card): List<SuggestedTag> {
        val texts: List<Pair<String, String>> = buildList {
            card.oracleText?.takeIf { it.isNotBlank() }
                ?.let { add("en" to it.lowercase()) }
            card.printedText?.takeIf { it.isNotBlank() }
                ?.let { add(card.lang.lowercase() to it.lowercase()) }
        }
        if (texts.isEmpty()) return emptyList()

        val results = mutableListOf<SuggestedTag>()

        TagDictionary.all().forEach { entry ->
            if (entry.patterns.isEmpty() || entry.baseConfidence <= 0f) return@forEach

            // Count how many language pattern groups produced at least one match.
            var matchedGroups = 0
            texts.forEach { (lang, text) ->
                val patterns = entry.patterns[lang].orEmpty()
                if (patterns.any { p -> text.contains(p) }) matchedGroups++
            }

            if (matchedGroups > 0) {
                val confidence = (entry.baseConfidence + 0.03f * (matchedGroups - 1))
                    .coerceIn(0f, 0.99f)
                results += SuggestedTag(
                    tag        = CardTag(entry.key, entry.category),
                    confidence = confidence,
                    source     = "strategy",
                )
            }
        }

        // Hot fix: Basic lands should not be tagged as "ramp"
        if (card.typeLine.contains("Basic Land", ignoreCase = true) || 
            card.typeLine.contains("Basic Snow Land", ignoreCase = true)) {
            results.removeAll { it.tag.key == "ramp" }
        }

        return results
    }
}
