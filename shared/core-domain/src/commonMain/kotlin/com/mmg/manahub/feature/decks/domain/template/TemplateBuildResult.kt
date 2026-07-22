package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.ManaColor

/** The staged progress [BuildDeckFromTemplateUseCase] emits (plan §3.3). [TOP_UP_FROM_COLLECTION]
 * (Wizard Quality Campaign B2b) runs after [RESOLVING_GAPS] and before [FILLING_LANDS]: it tops the
 * mainboard up to the format's target size from the owned pool regardless of category, closing the
 * gap a category-only fill can leave (the 2026-07-18 "Casual build returned 35 cards instead of 60"
 * bug). */
enum class BuildStage {
    VALIDATING, ANALYZING_COLLECTION, FETCHING_COMMUNITY, MAPPING_CATEGORIES,
    FILLING_FROM_COLLECTION, RESOLVING_GAPS, TOP_UP_FROM_COLLECTION, FILLING_LANDS, DONE,
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

/**
 * Deck Engine Unification plan (D3): a structured, honest declaration of "the build could not fill
 * this many slots from the owned collection above [BuildDeckFromTemplateUseCase.CATEGORY_FILL_FIT_FLOOR]"
 * -- REPLACES the old scalar [TemplateBuildResult] `shortfall: Int` (Wizard Quality Campaign B2b).
 * The wizard Result screen and Deck Studio's Suggestions tab render the SAME shape ("missing 3 Ramp
 * in {G}" / "missing 5 Other").
 *
 * @property categoryId the [TemplateCategory.id] this gap was attributed to, or [OTHER_GAP_CATEGORY_ID]
 *   for the reconciliation residual (see [BuildDeckFromTemplateUseCase]'s gap-building KDoc: the sum
 *   of every [DeckGap.missingCount] across the list is ALWAYS EXACTLY the true numeric shortfall --
 *   any slack the per-category attribution can't explain lands in one catch-all "Other" bucket rather
 *   than silently under/over-reporting).
 * @property colors the deck's own non-colorless color identity -- a coarse, honest simplification
 *   (per-category color-weighted attribution is future work): "missing N {label} in {colors}" reads
 *   the deck's overall colors, not a per-card color breakdown.
 * @property missingCount always > 0 (a satisfied category never appears in [TemplateBuildResult.gaps]).
 */
data class DeckGap(
    val categoryId: String,
    val categoryLabel: String,
    val colors: Set<ManaColor>,
    val missingCount: Int,
)

/** [DeckGap.categoryId] for the reconciliation residual bucket -- see [DeckGap]'s KDoc. */
const val OTHER_GAP_CATEGORY_ID = "other"

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
    /**
     * Deck Engine Unification plan (D3) -- REPLACES the old scalar `shortfall: Int` (Wizard Quality
     * Campaign B2b). Empty means the mainboard reached the format's full target size (lands
     * included). A non-empty list is an HONEST declaration that the owned collection had nothing
     * left clearing [BuildDeckFromTemplateUseCase.CATEGORY_FILL_FIT_FLOOR] -- the build NEVER places
     * a weak filler card just to hit the format's size; it reports the gap instead. See [DeckGap]'s
     * KDoc for the exact reconciliation guarantee (`gaps.sumOf { it.missingCount }` is always the
     * true numeric shortfall).
     */
    val gaps: List<DeckGap> = emptyList(),
)
