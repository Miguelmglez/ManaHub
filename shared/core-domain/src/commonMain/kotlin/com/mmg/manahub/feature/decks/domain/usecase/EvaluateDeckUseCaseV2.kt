package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.AnalysisEngine
import com.mmg.manahub.feature.decks.domain.engine.AnalysisWeights
import com.mmg.manahub.feature.decks.domain.engine.DeckAnalysis
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckProfile
import com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor

/**
 * Deck Analysis Engine v2 (plan `docs/plans/deck-analysis-engine-v2-plan.md`, Phase 2) — the SOLE
 * score/role-coverage/finding pipeline (D1 "total unification"). A thin, focused use case around
 * [AnalysisEngine]: given a mainboard + the SAME strategy resolution
 * [EvaluateDeckUseCase] already computed (pin wins, else [InferDeckArchetypeUseCase]), it produces
 * one [DeckAnalysis].
 *
 * Deliberately depends on the [DeckProfile] the LEGACY [com.mmg.manahub.feature.decks.domain.engine
 * .DeckScorer] already builds (passed in by the caller, never re-derived here) — see
 * [AnalysisEngine.evaluate]'s KDoc for why this is a reuse, not a coupling to the retiring analysis
 * path: [DeckProfile.tagFingerprint] (P4) and [ManaBaseAnalyzer]'s per-colour source math (P1) are
 * already CardTag-key / structural, not [com.mmg.manahub.feature.decks.domain.engine.DeckRole]-
 * based, so nothing here reads the legacy [com.mmg.manahub.feature.decks.domain.engine.DeckSkeleton]/
 * role-coverage machinery [AnalysisEngine] itself supersedes.
 *
 * Pure `commonMain`, zero Android imports, zero network/Scryfall calls (everything is derived from
 * the already-resolved mainboard) — the plan's "definition of done" gate.
 */
class EvaluateDeckUseCaseV2(
    private val manaBaseAnalyzer: ManaBaseAnalyzer = ManaBaseAnalyzer(),
) {

    /**
     * @param sideboardCount total sideboard card count (Wave 2 / B3), forwarded verbatim to
     *        [AnalysisEngine.evaluate]'s own [sideboardCount] param — see its KDoc. Appended LAST
     *        and defaulted so every existing call site keeps compiling unchanged.
     */
    operator fun invoke(
        mainboard: List<DeckEntry>,
        format: DeckFormat,
        colorIdentity: Set<ManaColor>,
        profile: DeckProfile,
        resolution: ArchetypeResolution,
        weights: AnalysisWeights = AnalysisWeights(),
        sideboardCount: Int = 0,
    ): DeckAnalysis = AnalysisEngine.evaluate(
        mainboard = mainboard,
        format = format,
        colorIdentity = colorIdentity,
        profile = profile,
        archetype = resolution.macro,
        themes = resolution.themes,
        isManualOverride = resolution.isManualOverride,
        confidence = resolution.confidence,
        weights = weights,
        manaBaseAnalyzer = manaBaseAnalyzer,
        sideboardCount = sideboardCount,
    )
}
