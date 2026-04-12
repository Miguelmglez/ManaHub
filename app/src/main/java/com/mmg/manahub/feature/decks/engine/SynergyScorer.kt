package com.mmg.manahub.feature.decks.engine

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardTag
import kotlin.math.abs

/**
 * Pure, stateless scoring logic. No Android dependencies.
 *
 * Final score = tagOverlap×0.55 + manaCurve×0.25 + colorIdentity×0.20 + ownedBonus×0.10
 * Clamped to [0, 1].
 */
object SynergyScorer {

    fun score(
        card:           Card,
        seedTags:       List<CardTag>,
        selectedColors: Set<ManaColor>,
        mainboard:      List<DeckEntry>,
        isOwned:        Boolean,
    ): CardSuggestion {
        val reasons = mutableListOf<String>()

        // ── Tag overlap (55%) ────────────────────────────────────────────────
        val cardTagSet  = card.tags.toSet()
        val overlap     = cardTagSet.intersect(seedTags.toSet())
        val tagScore    = when {
            seedTags.isEmpty() -> 0.5f
            overlap.isEmpty()  -> 0.0f
            else               -> (overlap.size.toFloat() / seedTags.size.toFloat()).coerceIn(0f, 1f)
        }
        if (overlap.isNotEmpty()) reasons += overlap.joinToString { it.label }

        // ── Mana curve fit (25%) ─────────────────────────────────────────────
        val avgCmc = if (mainboard.isEmpty()) 3.0
        else mainboard.sumOf { it.card.cmc * it.quantity } /
                mainboard.sumOf { it.quantity }.coerceAtLeast(1)
        val (curveScore, curveReason) = curveFitScore(card.cmc, avgCmc, mainboard.size)
        if (curveReason != null) reasons += curveReason

        // ── Color identity (20%) ──────────────────────────────────────────────
        val colorScore = colorScore(card, selectedColors, reasons)

        // ── Owned bonus (additive +10%) ───────────────────────────────────────
        val ownedBonus = if (isOwned) 0.10f else 0f
        if (isOwned) reasons += "In collection"

        val raw = tagScore * 0.55f + curveScore * 0.25f + colorScore * 0.20f + ownedBonus
        return CardSuggestion(
            card    = card,
            score   = raw.coerceIn(0f, 1f),
            reasons = reasons,
            isOwned = isOwned,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun curveFitScore(cmc: Double, avgCmc: Double, boardSize: Int): Pair<Float, String?> {
        if (boardSize < 10 && cmc <= 2.0) return 0.9f to "Low curve"
        return when (abs(cmc - avgCmc)) {
            in 0.0..1.0 -> 0.9f to null
            in 1.0..2.0 -> 0.6f to null
            else        -> 0.3f to null
        }
    }

    private fun colorScore(
        card:           Card,
        selectedColors: Set<ManaColor>,
        reasons:        MutableList<String>,
    ): Float {
        if (selectedColors.isEmpty()) return 0.7f
        val cardColors = card.colorIdentity
        if (cardColors.isEmpty()) { reasons += "Colorless"; return 0.8f }
        val selected = selectedColors.map { it.symbol }
        return if (cardColors.all { it in selected }) 1.0f else 0.0f
    }
}
