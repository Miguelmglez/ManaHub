package com.mmg.manahub.feature.decks.presentation.components

import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckProfile
import com.mmg.manahub.feature.decks.domain.template.SuggestionCategory
import com.mmg.manahub.feature.decks.domain.template.SuggestionCategoryResolver
import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion

/**
 * Presentation-side grouping by [SuggestionCategoryResolver] (Deck Builder v2 plan §3.8 / D3):
 * "all suggestions are grouped by suggestion category — never one flat mixed list". Grouping is
 * done HERE, in the UI layer, not inside [com.mmg.manahub.feature.decks.domain.orchestrator
 * .DeckDoctorOrchestrator] — its state shape stays a flat list (cheaper to keep the incremental
 * `AnalysisCache`/`recomputeIncremental` machinery untouched, per the plan's Phase 4 note), and the
 * Deck Wizard v2 Result screen needs the SAME grouping over a different shape ([DeckEntry], not
 * [AddSuggestion]) — so this lives as a small, reusable, UI-layer helper shared by both surfaces
 * rather than duplicated per screen.
 *
 * Deck Wizard 60-card wave (v6, plan §5 Phase 7.1): `groupCommunityAddSuggestions`
 * (`CommunityAddSuggestion`, Motor B) was deleted here -- its only producer,
 * `SuggestAddsFromCommunityUseCase`, had zero remaining production callers once the Motor A wizard
 * path was deleted (Motor B's OTHER half, "Decks like yours"/`FindSimilarDecksUseCase`, is a
 * separate use case and is unaffected).
 *
 * Every group is sorted by item count DESCENDING then category label ascending — deterministic,
 * never Room/collection insertion order.
 */
object SuggestionGrouping {

    fun groupDeckEntries(entries: List<DeckEntry>, profile: DeckProfile? = null): List<Pair<SuggestionCategory, List<DeckEntry>>> =
        entries
            .groupBy { SuggestionCategoryResolver.resolve(it.card, profile = profile) }
            .toList()
            .sortedWith(compareByDescending<Pair<SuggestionCategory, List<DeckEntry>>> { it.second.size }.thenBy { it.first.displayLabel })

    fun groupAddSuggestions(
        suggestions: List<AddSuggestion>,
        profile: DeckProfile? = null,
    ): List<Pair<SuggestionCategory, List<AddSuggestion>>> =
        suggestions
            .groupBy { SuggestionCategoryResolver.resolve(it.fit.card, profile = profile) }
            .toList()
            .sortedWith(compareByDescending<Pair<SuggestionCategory, List<AddSuggestion>>> { it.second.size }.thenBy { it.first.displayLabel })
}
