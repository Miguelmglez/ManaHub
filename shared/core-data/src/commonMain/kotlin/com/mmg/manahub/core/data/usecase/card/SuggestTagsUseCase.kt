package com.mmg.manahub.core.data.usecase.card

import com.mmg.manahub.core.data.tagging.GameChangerAnalyzer
import com.mmg.manahub.core.data.tagging.KeywordAnalyzer
import com.mmg.manahub.core.data.tagging.StrategyAnalyzer
import com.mmg.manahub.core.data.tagging.TypeLineAnalyzer
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.SuggestedTag

/**
 * Runs all four analyzers on a card and splits the suggestions into the
 * "high-confidence — auto-add" bucket and the "ask the user" bucket using
 * the supplied thresholds.
 *
 * Pure logic — no I/O. The [strategyAnalyzer] is injected because it depends on
 * tag-dictionary entries that are JVM-only; the three remaining analyzers are
 * stateless objects.
 *
 * Hilt builds this via `SharedDomainUseCaseModule` (the `@Inject` constructor was
 * removed because `javax.inject` is JVM-only and cannot be imported in `commonMain`).
 */
class SuggestTagsUseCase(
    private val strategyAnalyzer: StrategyAnalyzer,
) {

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
        strategyAnalyzer.analyze(card).forEach(::consider)
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
