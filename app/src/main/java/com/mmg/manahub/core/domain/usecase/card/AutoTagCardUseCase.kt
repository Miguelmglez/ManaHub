package com.mmg.manahub.core.domain.usecase.card

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.model.SuggestedTag
import com.mmg.manahub.core.tagging.GameChangerAnalyzer
import com.mmg.manahub.core.tagging.KeywordAnalyzer
import com.mmg.manahub.core.tagging.StrategyAnalyzer
import com.mmg.manahub.core.tagging.TypeLineAnalyzer
import javax.inject.Inject

/**
 * Runs all three analyzers on a card and splits the suggestions into the
 * "high-confidence — auto-add" bucket and the "ask the user" bucket using
 * the supplied thresholds.
 *
 * Pure logic — no I/O. Inject and call from [com.mmg.manahub.core.data.repository.CardRepositoryImpl]
 * whenever a card is freshly cached or refreshed from Scryfall.
 */
class SuggestTagsUseCase @Inject constructor() {

    data class Result(
        val confirmed: List<CardTag>,
        val suggested: List<SuggestedTag>,
    )

    operator fun invoke(
        card: Card,
        autoThreshold:    Float = DEFAULT_AUTO_THRESHOLD,
        suggestThreshold: Float = DEFAULT_SUGGEST_THRESHOLD,
    ): Result {
        // Dedupe by tag-key, keeping the highest confidence per key.
        val best = HashMap<String, SuggestedTag>()
        fun consider(s: SuggestedTag) {
            val cur = best[s.tag.key]
            if (cur == null || s.confidence > cur.confidence) best[s.tag.key] = s
        }
        KeywordAnalyzer.analyze(card).forEach(::consider)
        TypeLineAnalyzer.analyze(card).forEach(::consider)
        StrategyAnalyzer.analyze(card).forEach(::consider)
        GameChangerAnalyzer.analyze(card).forEach(::consider)

        val confirmed = mutableListOf<CardTag>()
        val suggested = mutableListOf<SuggestedTag>()
        best.values.forEach { s ->
            when {
                s.confidence >= autoThreshold    -> confirmed += s.tag
                s.confidence >= suggestThreshold -> suggested += s
                // else: discarded
            }
        }
        return Result(confirmed = confirmed.distinct(), suggested = suggested)
    }

    companion object {
        const val DEFAULT_AUTO_THRESHOLD    = 0.90f
        const val DEFAULT_SUGGEST_THRESHOLD = 0.60f
    }
}

/** Backwards-compatible alias kept so older injection sites still compile. */
typealias AutoTagCardUseCase = SuggestTagsUseCase
