package com.mmg.manahub.tools.tagpipeline.engine

import com.mmg.manahub.core.data.tagging.StrategyAnalyzer
import com.mmg.manahub.core.data.tagging.TagDictionary
import com.mmg.manahub.core.data.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.feature.decks.domain.engine.TribeDeriver

/**
 * The pipeline's ONE dependency on the production rule engine — this is the entire "zero drift"
 * guarantee of D5 in one place. Everything here is a thin, side-effect-free call into
 * `:shared:core-data`/`:shared:core-domain` code that ALSO runs in the live app.
 *
 * Deliberately reuses [SuggestTagsUseCase] rather than calling [StrategyAnalyzer] directly: it is
 * the exact same class `:app`'s auto-tag flow constructs (see `SharedDomainUseCaseModule
 * .provideComputeCardTagsUseCase` / `AutoTagCard` in `:app`), including the SAME confidence
 * dedup-by-key logic and the SAME `DEFAULT_AUTO_THRESHOLD` (0.90f) that decides which tags a user
 * sees auto-applied on a card. Reusing the whole use case (not just the analyzer it wraps) is a
 * stronger drift guarantee than reimplementing the auto/suggest split in the CLI.
 *
 * [TribeDeriver] is NOT part of [SuggestTagsUseCase] (tribes are a `DeckScorer`-internal
 * fingerprinting concept, not a persisted `CardTag`), so it is called separately, per the plan's own
 * "+ TribeDeriver.subtypeKeys" instruction — this pipeline uses [TribeDeriver.tribeKeys] (subtypes ∪
 * oracle-text tribal payoffs), the fuller signal `DeckScorer.profile` itself consumes, rather than
 * the narrower [TribeDeriver.subtypeKeys] alone.
 */
class CardTagEngine {

    private val suggestTagsUseCase = SuggestTagsUseCase(
        strategyAnalyzer = StrategyAnalyzer(entriesProvider = { TagDictionary.all() }),
    )

    /** The production auto-confirmed [com.mmg.manahub.core.model.CardTag] keys for [card]. */
    fun confirmedTagKeys(card: Card): Set<String> =
        suggestTagsUseCase(card).confirmed.map { it.key }.toSet()

    /**
     * Bare tribe words (the `tribe:` fingerprint prefix stripped — see
     * [com.mmg.manahub.tools.tagpipeline.model.CardStrategyTagsRow]'s KDoc for why the prefix is
     * dropped in the exported row).
     */
    fun tribeWords(card: Card): Set<String> =
        TribeDeriver.tribeKeys(card).map { it.removePrefix(TribeDeriver.TRIBE_PREFIX) }.toSet()
}
