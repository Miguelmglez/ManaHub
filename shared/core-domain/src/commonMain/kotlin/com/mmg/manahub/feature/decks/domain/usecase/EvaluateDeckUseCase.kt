package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeData
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeEvaluator
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeSkeletonResolver
import com.mmg.manahub.feature.decks.domain.engine.AnalysisWeights
import com.mmg.manahub.feature.decks.domain.engine.CurveExemption
import com.mmg.manahub.feature.decks.domain.engine.DeckAnalysis
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckEvaluation
import com.mmg.manahub.feature.decks.domain.engine.DeckProfile
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.DeckWarning
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.ResolvedArchetypeSkeleton
import com.mmg.manahub.feature.decks.domain.engine.ScoreWeights
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Computes the read-only "Health" evaluation of a deck's mainboard using the new
 * [DeckScorer] engine.
 *
 * The caller (the ViewModel) resolves each deck slot to a full [Card] and passes the
 * mainboard in as a list of [DeckEntry]; this use case stays pure and easily testable
 * (no repositories, no Card resolution). It derives the deck's color identity from the
 * mainboard plus an optional commander identity, builds the [DeckProfile] and returns the
 * resulting [DeckEvaluation] together with the profile (the Health view needs the profile's
 * role skeleton / ideals).
 *
 * Strategy seed inference (Phase 7 / plan B4) is the caller's responsibility: the Deck Doctor
 * ViewModel runs [InferDeckIdentityUseCase] over the commander + the deck's highest-weight identity
 * cards and passes the resulting `seedTags` here, so the fingerprint is no longer purely
 * self-referential. Callers that have no seeds (e.g. an empty-strategy preview) simply pass the
 * default empty list.
 *
 * ## Archetype-aware layer (Deck Doctor Community/Archetype plan, Phase 1.6)
 * [DeckScorer.evaluate] itself is called EXACTLY as before Phase 1 (same 3 args, same
 * [DeckScorer]/[com.mmg.manahub.feature.decks.domain.engine.DeckSkeleton] engine) — the base
 * evaluation is untouched. When the resolved archetype ([InferDeckArchetypeUseCase] or the
 * deck's own `archetypeOverride`) is [ArchetypeId.GENERIC] with NO themes, this use case returns
 * that base [DeckHealth] verbatim: byte-identical to pre-Phase-1 behavior (the documented exit
 * criterion — see `project_archetype_engine` memory). Only when a SPECIALIZED archetype or at
 * least one theme resolves does the archetype layer additionally run: it replaces the base
 * evaluation's land/curve/[com.mmg.manahub.feature.decks.domain.engine.DeckWarning.MissingRole]
 * warnings with the archetype-aware equivalents (D18 land union rule, archetype curve band, the
 * new [RoleKey][com.mmg.manahub.feature.decks.domain.engine.RoleKey]-keyed role-gap/anti-role
 * warnings) while leaving construction/mana-base/synergy warnings from the base evaluation intact.
 *
 * ## Deck Analysis Engine v2 (Phase 2) — [DeckHealth.analysis], additive
 * Every invocation ALSO runs [evaluateDeckUseCaseV2] (the new, unified pillar pipeline — see its
 * KDoc) on top of the SAME [DeckProfile]/[ArchetypeResolution] this class already computes for the
 * legacy path above, and attaches the result as [DeckHealth.analysis]. This is a **wrap, not a
 * replace**: the legacy `evaluation`/`profile`/`archetypeResolution` fields above stay computed
 * EXACTLY as before (byte-identical) because the currently-live `DeckStudioScreen` Suggestions tab
 * (health ring, role coverage, warnings) still reads them directly — only the Cuts/Adds/Community
 * sections are hidden behind `DECK_STUDIO_SUGGESTIONS_ENGINE_ENABLED` (Phase 0), not the health
 * display. `DeckHealth.analysis` is inert extra data until the Phase 3 UI migration consumes it.
 * Unlike the legacy path, [evaluateDeckUseCaseV2] runs UNCONDITIONALLY (even GENERIC/no-themes and
 * Draft) — see [AnalysisEngine][com.mmg.manahub.feature.decks.domain.engine.AnalysisEngine]'s KDoc.
 */
class EvaluateDeckUseCase(
    private val deckScorer: DeckScorer,
    private val progressionEventBus: ProgressionEventBus,
    // ioDispatcher stays 3rd (its pre-Phase-1 position) so existing POSITIONAL constructor calls
    // (tests passing a TestDispatcher as the 3rd arg) keep compiling unchanged; the new Phase-1
    // param is appended LAST instead of being inserted before it.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val inferDeckArchetypeUseCase: InferDeckArchetypeUseCase = InferDeckArchetypeUseCase(),
    // Deck Analysis Engine v2 Phase 2 — appended LAST (same "new optional params go at the end"
    // rule as [inferDeckArchetypeUseCase] above) so no existing positional-arg call site changes.
    private val evaluateDeckUseCaseV2: EvaluateDeckUseCaseV2 = EvaluateDeckUseCaseV2(),
    // Deck Analysis Engine v2 Phase 4 (telemetry/resilience) — nullable-defaulted, appended LAST,
    // mirrors [com.mmg.manahub.feature.decks.domain.template.DeckTemplateResolver]'s own
    // [CrashReporter] param so no existing positional-arg test/call site breaks. Lets [invoke] guard
    // the still-freshly-calibrated [evaluateDeckUseCaseV2] call (see its own KDoc there) without
    // forcing every caller/test to supply a reporter.
    private val crashReporter: CrashReporter? = null,
) {

    /** Stable, code-side slug identifying the Deck Doctor feature for exploration quests. */
    private companion object {
        const val FEATURE_DECK_DOCTOR = "deck_doctor"
    }

    /**
     * @param mainboard the non-sideboard deck entries, each with a resolved [Card].
     * @param format the deck's [DeckFormat].
     * @param commanderIdentity color symbols of the deck's commander (empty for non-commander
     *        decks or when the commander card could not be resolved). Each symbol is a single
     *        letter from `W/U/B/R/G`; anything else is ignored.
     * @param seedTags inferred strategy seed tags (plan B4) — the commander's + the deck's
     *        highest-weight identity cards' tags, produced by [InferDeckIdentityUseCase]. Floored
     *        into the fingerprint by the scorer (size-independent, Phase-3 `SEED_FLOOR`). Empty when
     *        the deck carries no recognizable strategy signal.
     * @param weights the (optionally debug-tuned) [ScoreWeights] threaded through the Deck Doctor
     *        read path (plan F2), forwarded to [DeckScorer.evaluate]. The default keeps every other
     *        caller (and all existing tests) byte-identical.
     * @param archetypeOverride raw `ArchetypeId.name` from [com.mmg.manahub.core.model.Deck
     *        .archetypeOverride], or null/blank/unrecognized to let the classifier infer. Parsed
     *        via `entries.firstOrNull { }`, never `.valueOf()`.
     * @param themesOverride raw `ThemeId.name` strings from [com.mmg.manahub.core.model.Deck
     *        .themesOverride] (paired with [archetypeOverride] — a theme pin with no macro pin is
     *        still honored, layered on the INFERRED macro). Unrecognized entries are dropped.
     * @param commanderTags the resolved commander card's tags (A.6 "commander tags" classifier
     *        signal); empty for non-Commander decks or an unresolved commander.
     * @param analysisWeights the (optionally debug-tuned) [AnalysisWeights] threaded through to
     *        [evaluateDeckUseCaseV2] (Deck Analysis Engine v2 Phase 2). The default keeps every
     *        caller byte-identical to pre-Phase-2 behavior for the LEGACY `evaluation`/`profile`
     *        fields — only the new, additive [DeckHealth.analysis] field is affected.
     * @param sideboardCount total sideboard card count (Wave 2 / B3), forwarded to
     *        [evaluateDeckUseCaseV2] for P5's [com.mmg.manahub.feature.decks.domain.engine.Finding
     *        .SideboardOversized] check. Appended LAST and defaulted so every existing call site
     *        keeps compiling unchanged.
     * @return a [DeckHealth] bundling the [DeckEvaluation], the [DeckProfile] it was built from,
     *         the resolved [ArchetypeResolution] (always present — GENERIC/no-themes when the
     *         deck carries no archetype signal, so the Studio header chip always has something to
     *         render), and the v2 [DeckAnalysis] (always present, see [DeckHealth.analysis]'s KDoc).
     */
    suspend operator fun invoke(
        mainboard: List<DeckEntry>,
        format: DeckFormat,
        commanderIdentity: Set<String> = emptySet(),
        seedTags: List<CardTag> = emptyList(),
        weights: ScoreWeights = ScoreWeights(),
        archetypeOverride: String? = null,
        themesOverride: List<String> = emptyList(),
        commanderTags: List<CardTag> = emptyList(),
        analysisWeights: AnalysisWeights = AnalysisWeights(),
        sideboardCount: Int = 0,
    ): DeckHealth = withContext(ioDispatcher) {
        val colorIdentity = deriveColorIdentity(mainboard, commanderIdentity)

        val profile = deckScorer.profile(
            mainboard = mainboard,
            format = format,
            colorIdentity = colorIdentity,
            seedTags = seedTags, // B4: inferred strategy seed from the caller (Phase 7)
        )

        val nonLand = mainboard.filterNot { BasicLandCalculator.isLand(it.card) }
        // Pass the FULL mainboard so the scorer can run construction validation (C5):
        // deck size, copy limits, Commander singleton + off-color-identity checks.
        val evaluation = deckScorer.evaluate(profile, nonLand, fullMainboard = mainboard, weights = weights)

        // Canonical Deck Doctor "analysis produced" choke point: a successful evaluation is the single
        // domain event for the EXPLORATION quest (`daily_explore_deck_doctor`). Emitted here (not the
        // ViewModel) per ADR-002 §1; the per-day idempotency key (FeatureExplored) means re-running on
        // a cut/add the same day advances the quest at most once. The emit grants 0 XP (no ledger row);
        // it is fire-and-forget on the bus and never affects the returned analysis. Fires EXACTLY ONCE
        // per invoke() call — the v2 pipeline below reuses this SAME call's profile/resolution, it
        // does not trigger a second emit.
        progressionEventBus.emit(
            ProgressionEvent.FeatureExplored(
                featureKey = FEATURE_DECK_DOCTOR,
                occurredAt = Clock.System.now(),
            )
        )

        val archetypeFormat = ArchetypeFormat.of(format)
        // The resolution used by BOTH the legacy layer below and the v2 pipeline — computed once,
        // GENERIC/no-themes for Draft (no archetype skeleton exists for it, mirrors the pre-Phase-1
        // Draft short-circuit).
        val resolution = if (archetypeFormat == null) {
            ArchetypeResolution(ArchetypeId.GENERIC, emptyList(), isManualOverride = false, confidence = 0f)
        } else {
            resolveArchetype(mainboard, archetypeFormat, archetypeOverride, themesOverride, commanderTags)
        }

        // Deck Analysis Engine v2 (Phase 2) — runs UNCONDITIONALLY (even GENERIC/no-themes, even
        // Draft), unlike the legacy layer below which zero-regression-skips those cases. See this
        // class's KDoc "Deck Analysis Engine v2" section for why this is purely additive.
        //
        // Phase 4 resilience fix: [AnalysisEngine] is brand-new (Phase 2, not yet exercised against
        // a real production-diversity corpus at the time this guard was added) and runs with NO
        // try/catch anywhere between it and the ViewModel's `viewModelScope`
        // ([com.mmg.manahub.feature.decks.domain.orchestrator.DeckDoctorOrchestrator.loadAnalysis]'s
        // `scope.launch` has none either) -- an uncaught exception in any of its 5 pillar evaluators
        // would crash the WHOLE app, not just the Analysis tab. [DeckHealth.analysis] is already
        // nullable (see its own KDoc), so a `null` fallback here needs no consumer change; every
        // OTHER field this method computes (the legacy `evaluation`/`profile`/`archetypeResolution`)
        // stays fully unaffected by a v2 failure.
        val analysis: DeckAnalysis? = runCatching {
            evaluateDeckUseCaseV2(
                mainboard = mainboard,
                format = format,
                colorIdentity = colorIdentity,
                profile = profile,
                resolution = resolution,
                weights = analysisWeights,
                sideboardCount = sideboardCount,
            )
        }.onFailure { t ->
            crashReporter?.setCustomKey("deck_analysis_format", format.name)
            crashReporter?.setCustomKey("deck_analysis_error_type", t::class.simpleName ?: "Unknown")
            crashReporter?.log("deck_analysis_v2_engine_failed")
            crashReporter?.recordException(RuntimeException("[EvaluateDeckUseCase] deck_analysis_v2_engine_failed", t))
        }.getOrNull()

        if (archetypeFormat == null) {
            // Draft has no archetype skeleton (Appendix A defines none) — always the pre-Phase-1
            // GENERIC/no-themes path, verbatim base evaluation.
            return@withContext DeckHealth(evaluation = evaluation, profile = profile, archetypeResolution = resolution, analysis = analysis)
        }

        if (resolution.macro == ArchetypeId.GENERIC && resolution.themes.isEmpty()) {
            // Zero-regression path (1.4/1.8 exit criterion): GENERIC-with-no-themes returns the
            // BASE evaluation untouched — no archetype warnings computed or substituted.
            return@withContext DeckHealth(evaluation = evaluation, profile = profile, archetypeResolution = resolution, analysis = analysis)
        }

        val archetypeEvaluation = applyArchetypeLayer(
            baseEvaluation = evaluation,
            mainboard = mainboard,
            nonLand = nonLand,
            format = archetypeFormat,
            colorIdentity = colorIdentity,
            resolution = resolution,
        )
        DeckHealth(evaluation = archetypeEvaluation, profile = profile, archetypeResolution = resolution, analysis = analysis)
    }

    /** Override wins when set and recognized; otherwise runs [InferDeckArchetypeUseCase]. */
    private fun resolveArchetype(
        mainboard: List<DeckEntry>,
        format: ArchetypeFormat,
        archetypeOverride: String?,
        themesOverride: List<String>,
        commanderTags: List<CardTag>,
    ): ArchetypeResolution {
        val pinnedMacro = archetypeOverride?.let { raw -> ArchetypeId.entries.firstOrNull { it.name == raw } }
        val pinnedThemes = themesOverride.mapNotNull { raw -> ThemeId.entries.firstOrNull { it.name == raw } }.take(2)

        if (pinnedMacro != null || pinnedThemes.isNotEmpty()) {
            return ArchetypeResolution(
                macro = pinnedMacro ?: ArchetypeId.GENERIC,
                themes = pinnedThemes,
                isManualOverride = true,
                confidence = 1f,
            )
        }
        val inferred = inferDeckArchetypeUseCase(mainboard, format, commanderTags)
        return ArchetypeResolution(inferred.macro, inferred.themes, isManualOverride = false, confidence = inferred.confidence)
    }

    /**
     * Resolves the skeleton, computes the archetype role-count snapshot, evaluates via
     * [ArchetypeEvaluator], and merges the result into [baseEvaluation]: land/curve/[DeckWarning
     * .MissingRole] warnings from the base evaluation are DROPPED (superseded — avoids duplicate,
     * possibly-contradictory warnings against two different skeletons) and replaced by the
     * archetype-aware set; every other base warning (construction, mana-base, synergy-density,
     * unresolved-cards) is preserved verbatim.
     */
    private fun applyArchetypeLayer(
        baseEvaluation: DeckEvaluation,
        mainboard: List<DeckEntry>,
        nonLand: List<DeckEntry>,
        format: ArchetypeFormat,
        colorIdentity: Set<ManaColor>,
        resolution: ArchetypeResolution,
    ): DeckEvaluation {
        val colorCount = colorIdentity.size
        val skeleton: ResolvedArchetypeSkeleton = ArchetypeSkeletonResolver.resolveWithColor(
            format = format,
            archetype = resolution.macro,
            themes = resolution.themes,
            identity = colorIdentity,
        )
        val roleCounts = ArchetypeRoleClassifier.deckRoleCounts(mainboard)
        // Lands are not a RoleSpec key; count them directly from the mainboard (mirrors
        // DeckScorer's own LAND short-circuit).
        val landCount = mainboard.filter { entry -> BasicLandCalculator.isLand(entry.card) }.sumOf { it.quantity }
        val nonLandCount = nonLand.sumOf { it.quantity }
        val avgCmc = if (nonLandCount == 0) 0.0 else nonLand.sumOf { it.card.cmc * it.quantity } / nonLandCount

        val curveExemptionActive = ThemeId.REANIMATOR in resolution.themes &&
            CurveExemption.REANIMATOR_HIGH_MV in skeleton.curveExemptions &&
            run {
                val enablerBand = skeleton.roleTargets["graveyard_enabler"]
                val reanimationBand = skeleton.roleTargets["reanimation"]
                val enablerHave = roleCounts["graveyard_enabler"] ?: 0
                val reanimationHave = roleCounts["reanimation"] ?: 0
                enablerBand != null && reanimationBand != null &&
                    enablerHave >= enablerBand.min && reanimationHave >= reanimationBand.min
            }

        val colorModulation = if (colorCount <= 0) null else
            ArchetypeData.COLOR_MODULATION.getValue(format)[ArchetypeData.colorCountBucket(colorCount)]

        val archetypeWarnings = ArchetypeEvaluator.evaluate(
            roleCounts = roleCounts,
            lands = landCount,
            avgMv = avgCmc,
            skeleton = skeleton,
            colorModulation = colorModulation,
            curveExemptionActive = curveExemptionActive,
        )

        val supersededWarnings = baseEvaluation.warnings.filterNot { warning ->
            warning is DeckWarning.TooFewLands || warning is DeckWarning.TooManyLands ||
                warning is DeckWarning.MissingRole ||
                warning is DeckWarning.CurveTooHigh || warning is DeckWarning.CurveTooLow
        }
        return baseEvaluation.copy(warnings = supersededWarnings + archetypeWarnings)
    }

    /**
     * Color identity = union of every mainboard card's [Card.colorIdentity] symbols plus the
     * commander's identity, mapped to [ManaColor]. Only the five WUBRG letters map to a color;
     * "C" (colorless) and any unknown symbol are dropped so an empty result correctly means
     * "no color restriction" for the scorer.
     */
    private fun deriveColorIdentity(
        mainboard: List<DeckEntry>,
        commanderIdentity: Set<String>,
    ): Set<ManaColor> {
        val symbols = buildSet {
            mainboard.forEach { entry -> addAll(entry.card.colorIdentity) }
            addAll(commanderIdentity)
        }
        return symbols.mapNotNull(::symbolToColor).toSet()
    }

    private fun symbolToColor(symbol: String): ManaColor? = when (symbol.uppercase()) {
        ManaColor.W.symbol -> ManaColor.W
        ManaColor.U.symbol -> ManaColor.U
        ManaColor.B.symbol -> ManaColor.B
        ManaColor.R.symbol -> ManaColor.R
        ManaColor.G.symbol -> ManaColor.G
        else -> null // "C" and unknown symbols are not a color-identity restriction
    }
}

/**
 * The archetype/theme resolved for a [DeckHealth] (Deck Doctor Community/Archetype plan, Phase
 * 1.6/1.7) — always present, defaulting to `GENERIC`/no-themes when the deck carries no signal.
 * Drives the Studio Suggestions header chip ("Deck plan: <Archetype> · <themes>") and its
 * detected-vs-manual indicator.
 */
data class ArchetypeResolution(
    val macro: ArchetypeId,
    val themes: List<ThemeId>,
    val isManualOverride: Boolean,
    val confidence: Float,
)

/**
 * Result of [EvaluateDeckUseCase]: the deck [evaluation] plus the [profile] it was derived from
 * (exposed because the Health UI renders role ideals / skeleton, which live on the profile), plus
 * the [archetypeResolution] used to compute [evaluation]'s archetype-aware warnings (if any).
 *
 * @property analysis Deck Analysis Engine v2 (Phase 2) — the new, unified pillar-based
 *           [DeckAnalysis], ADDITIVE to [evaluation]/[profile]/[archetypeResolution] above (a
 *           "wrap, not replace" — see [EvaluateDeckUseCase]'s KDoc). [EvaluateDeckUseCase.invoke]
 *           ALWAYS populates this with a real value; it stays nullable only so any OTHER
 *           [DeckHealth] construction site (tests, future callers) keeps compiling without having
 *           to fabricate a [DeckAnalysis]. Null-safe consumers should treat `null` as "not yet
 *           evaluated by the v2 pipeline", never as an error state.
 */
data class DeckHealth(
    val evaluation: DeckEvaluation,
    val profile: DeckProfile,
    val archetypeResolution: ArchetypeResolution = ArchetypeResolution(ArchetypeId.GENERIC, emptyList(), isManualOverride = false, confidence = 0f),
    val analysis: DeckAnalysis? = null,
)
