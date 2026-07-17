package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry

/** The staged progress [BuildDeckFromTemplateUseCase] emits (plan §3.3). */
enum class BuildStage {
    VALIDATING, ANALYZING_COLLECTION, FETCHING_COMMUNITY, MAPPING_CATEGORIES,
    FILLING_FROM_COLLECTION, RESOLVING_GAPS, FILLING_LANDS, DONE,
}

/** One [BuildDeckFromTemplateUseCase] progress event -- a [Stage] label, a [Failed] validation/
 * fetch abort, or the final [Complete] result. */
sealed class TemplateBuildProgress {
    data class Stage(val stage: BuildStage) : TemplateBuildProgress()
    data class Complete(val result: TemplateBuildResult) : TemplateBuildProgress()
    data class Failed(val stage: BuildStage, val message: String) : TemplateBuildProgress()
}

/** One unowned card the template wants for a category -- view-only (D8): never auto-added, the UI
 * offers an explicit per-card "Add" button. */
data class TemplateCardSuggestion(
    val card: Card,
    val weight: Float,
    val suggestedCopies: Int = 1,
)

/** Unowned suggestions for one category -- ALWAYS structurally separate from [TemplateBuildResult
 * .deckCards] (D3: collection and community suggestions are never mixed in the same list). */
data class CategorySuggestions(
    val category: SuggestionCategory,
    val suggestions: List<TemplateCardSuggestion>,
)

/** How much of a category's target the fill stage actually reached -- feeds both the result UI and
 * (via [TemplateBuildResult.archetypeOverride]/[TemplateBuildResult.themesOverride]) keeps the Deck
 * Doctor coherent with what the builder just did. */
data class CategoryFill(
    val category: SuggestionCategory,
    val filled: Int,
    val target: Int,
)

/** [BuildDeckFromTemplateUseCase]'s terminal result (plan §3.3 step 8). */
data class TemplateBuildResult(
    /** Owned cards written to the live draft -- seeds first, then collection fills, then
     * materialized basics. Never mixed with [communitySuggestions]. */
    val deckCards: List<DeckEntry>,
    val communitySuggestions: List<CategorySuggestions>,
    val report: List<CategoryFill>,
    val templateSource: TemplateSource,
    val archetypeInfo: DeckTemplateArchetypeInfo,
    /** Raw `ArchetypeId.name` -- write straight onto `Deck.archetypeOverride` (CLAUDE.md: never
     * `.valueOf()`, always the raw enum-name string; mirrors `DeckDoctorOrchestrator`'s own
     * `archetypeId?.name` precedent) so `EvaluateDeckUseCase` evaluates against the SAME skeleton
     * this build just filled against (plan §3.3 "Builder<->Doctor coherence"). */
    val archetypeOverride: String,
    val themesOverride: List<String>,
    /** D9: Casual/60-card recommends <=2 colors; true when the wizard's chosen color count is 3+. */
    val colorConsistencyWarning: Boolean,
    /** One-sentence strategy line (`SeedStrategy.description`) surfaced on the result screen. */
    val gamePlan: String?,
)
