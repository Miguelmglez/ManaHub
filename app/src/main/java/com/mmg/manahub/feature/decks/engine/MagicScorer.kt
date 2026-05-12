package com.mmg.manahub.feature.decks.engine

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardTag
import kotlin.math.abs

/**
 * Result of a magic scoring calculation.
 */
data class MagicMatchResult(
    val score: Float,
    val reasons: List<String>
)

/**
 * Pure logic for scoring card relevance based on multi-layered tags.
 * 
 * Final score calculation:
 * - Tag Score (60%): Weighted sum of matching User, Auto, and Suggested tags.
 * - Mana Curve (20%): How well the card fits the current deck's average CMC.
 * - Color Identity (20%): Consistency with the selected deck colors.
 */
object MagicScorer {

    // Weights for different tag types
    private const val WEIGHT_USER_TAG = 1.2f
    private const val WEIGHT_AUTO_TAG = 1.0f
    private const val WEIGHT_SUGGESTED_TAG_CONFIRMED = 1.0f
    private const val WEIGHT_SUGGESTED_TAG_UNCONFIRMED = 0.5f

    fun score(
        card: Card,
        targetTags: List<CardTag>,
        selectedColors: Set<ManaColor>,
        currentAverageCmc: Double,
        deckSize: Int
    ): MagicMatchResult {
        val reasons = mutableListOf<String>()

        // ── 1. Tag Scoring (60%) ─────────────────────────────────────────────
        val tagScore = calculateTagScore(card, targetTags, reasons)

        // ── 2. Mana Curve Fit (20%) ──────────────────────────────────────────
        val curveScore = calculateCurveScore(card.cmc, currentAverageCmc, deckSize, reasons)

        // ── 3. Color Identity (20%) ──────────────────────────────────────────
        val colorScore = calculateColorScore(card, selectedColors, reasons)

        val totalScore = (tagScore * 0.60f + curveScore * 0.20f + colorScore * 0.20f)
            .coerceIn(0f, 1f)

        return MagicMatchResult(totalScore, reasons)
    }

    private fun calculateTagScore(
        card: Card,
        targetTags: List<CardTag>,
        reasons: MutableList<String>
    ): Float {
        if (targetTags.isEmpty()) return 0.5f

        var matchWeight = 0f
        var maxPossibleWeight = targetTags.size.toFloat() // Simplified base

        val targetKeys = targetTags.map { it.key }.toSet()

        // Check User Tags (highest weight)
        card.userTags.filter { it.key in targetKeys }.forEach { tag ->
            matchWeight += WEIGHT_USER_TAG
            reasons.add("User Tag: ${tag.label}")
        }

        // Check Auto Tags
        card.tags.filter { it.key in targetKeys }.forEach { tag ->
            // Avoid double counting if already in user tags (user tag takes precedence in weight)
            if (card.userTags.none { it.key == tag.key }) {
                matchWeight += WEIGHT_AUTO_TAG
                reasons.add("Tag: ${tag.label}")
            }
        }

        // Check Suggested Tags
        card.suggestedTags.filter { it.tag.key in targetKeys }.forEach { suggestion ->
            if (card.userTags.none { it.key == suggestion.tag.key } &&
                card.tags.none { it.key == suggestion.tag.key }) {

                // If confidence is high or confirmed (this logic might be extended)
                val weight = if (suggestion.confidence >= 0.8f) WEIGHT_SUGGESTED_TAG_CONFIRMED
                else WEIGHT_SUGGESTED_TAG_UNCONFIRMED

                matchWeight += weight
                reasons.add("Suggested: ${suggestion.tag.label}")
            }
        }

        return (matchWeight / maxPossibleWeight).coerceIn(0f, 1.2f) / 1.2f // Normalize roughly
    }

    private fun calculateCurveScore(
        cmc: Double,
        avgCmc: Double,
        deckSize: Int,
        reasons: MutableList<String>
    ): Float {
        // Early deck building (less than 10 cards): prioritize low CMC
        if (deckSize < 10 && cmc <= 2.0) {
            reasons.add("Early curve priority")
            return 0.9f
        }

        val diff = abs(cmc - avgCmc)
        return when {
            diff <= 1.0 -> 1.0f
            diff <= 2.0 -> 0.6f
            else -> 0.2f
        }
    }

    private fun calculateColorScore(
        card: Card,
        selectedColors: Set<ManaColor>,
        reasons: MutableList<String>
    ): Float {
        if (selectedColors.isEmpty()) return 1.0f

        val cardColorIdentity = card.colorIdentity
        if (cardColorIdentity.isEmpty()) {
            reasons.add("Colorless fit")
            return 0.9f
        }

        val allowedSymbols = selectedColors.map { it.symbol }.toSet()
        val isCompatible = cardColorIdentity.all { it in allowedSymbols }

        return if (isCompatible) 1.0f else 0.0f
    }
}



















