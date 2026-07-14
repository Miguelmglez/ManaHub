package com.mmg.manahub.core.tagging

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.SuggestedTag
import com.mmg.manahub.core.model.TagDictionaryEntry

// ═══════════════════════════════════════════════════════════════════════════════
//  KMP migration compatibility layer.
//
//  The four analyzers (KeywordAnalyzer, TypeLineAnalyzer, GameChangerAnalyzer,
//  StrategyAnalyzer) moved to :shared:core-data `commonMain` during the KMP
//  migration. The three stateless objects are re-exported via typealiases below.
//
//  StrategyAnalyzer became a *class* (constructor-injected entries provider)
//  because it depends on TagDictionary, which is JVM-only. This file provides
//  a convenience *object* wrapper backed by TagDictionary.all() so that existing
//  :app call sites (tests, CardRepositoryImpl comments) keep compiling with the
//  same `StrategyAnalyzer.analyze(card)` syntax.
// ═══════════════════════════════════════════════════════════════════════════════

/** Re-export: shared object, same API. */
typealias KeywordAnalyzer = com.mmg.manahub.core.data.tagging.KeywordAnalyzer

/** Re-export: shared object, same API. */
typealias TypeLineAnalyzer = com.mmg.manahub.core.data.tagging.TypeLineAnalyzer

/** Re-export: shared object, same API. */
typealias GameChangerAnalyzer = com.mmg.manahub.core.data.tagging.GameChangerAnalyzer

/**
 * App-side convenience wrapper that delegates to the shared [com.mmg.manahub.core.data.tagging.StrategyAnalyzer]
 * class backed by [TagDictionary.all].
 *
 * This lets existing `:app` call sites keep using `StrategyAnalyzer.analyze(card)` without
 * needing to construct the class themselves. New shared code should inject the class directly.
 */
object StrategyAnalyzer {
    private val delegate = com.mmg.manahub.core.data.tagging.StrategyAnalyzer(
        entriesProvider = { TagDictionary.all() },
    )

    /** Analyze [card] using the current [TagDictionary] entries. */
    fun analyze(card: Card): List<SuggestedTag> = delegate.analyze(card)
}

/**
 * Creates a [com.mmg.manahub.core.data.tagging.StrategyAnalyzer] instance backed by the app-side
 * [TagDictionary]. Used by DI modules to provide the shared class to [com.mmg.manahub.core.data.usecase.card.SuggestTagsUseCase].
 */
fun createStrategyAnalyzer(): com.mmg.manahub.core.data.tagging.StrategyAnalyzer =
    com.mmg.manahub.core.data.tagging.StrategyAnalyzer(
        entriesProvider = { TagDictionary.all() },
    )
