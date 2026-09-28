package com.mmg.manahub.feature.decks.harness

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Wizard 60-card wave (v6, plan §5 Phase 7.1): the Wizard Quality Campaign's Motor-A-era
//  BuildMetrics/HarnessMetricsCalculator.compute()/forFailedBuild() -- and every import/constant/
//  private field they alone needed (BasicLandCalculator, ArchetypeFormat/ArchetypeSkeletonResolver,
//  CATEGORY_FILL_FIT_FLOOR, CardFit, DeckEntry, DeckProfile, DeckScorer, DeckWarning, ManaBaseAnalyzer,
//  ManaColor, NeutralPowerResolver, RoleClassifier, SuggestionCategoryResolver, TemplateBuildResult,
//  AddSuggestion, COHERENCE_SWAP_MARGIN/ROUND_TRIP_TOP_CUT_WINDOW/ROUND_TRIP_GOOD_FIT_THRESHOLD --
//  were deleted here: their only callers were WizardQualityMatrixTest/HarnessMatrixRunner/
//  P0BaselineTest, all deleted in the same pass. `HarnessMetricsCalculator.isLegal` SURVIVES --
//  WizardCommanderMatrixV2.specs() still calls it.
// ═══════════════════════════════════════════════════════════════════════════════

object HarnessMetricsCalculator {

    /** Mirrors the legality gate every wizard/analysis call site shares. */
    fun isLegal(card: Card, format: DeckFormat): Boolean {
        fun ok(s: String) = s.equals("legal", true) || s.equals("restricted", true)
        return when (format) {
            DeckFormat.STANDARD -> ok(card.legalityStandard)
            DeckFormat.PIONEER -> ok(card.legalityPioneer)
            DeckFormat.MODERN -> ok(card.legalityModern)
            DeckFormat.LEGACY -> ok(card.legalityLegacy)
            DeckFormat.VINTAGE -> ok(card.legalityVintage)
            DeckFormat.PAUPER -> ok(card.legalityPauper)
            DeckFormat.COMMANDER -> ok(card.legalityCommander)
            DeckFormat.CASUAL, DeckFormat.DRAFT, DeckFormat.COMMANDER_CASUAL -> true
        }
    }
}
