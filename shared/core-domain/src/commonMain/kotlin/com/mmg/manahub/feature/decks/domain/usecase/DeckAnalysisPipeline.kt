package com.mmg.manahub.feature.decks.domain.usecase
// COMMENTS_REVIEWED: 2026-09-08

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.ScoreWeightOverrides
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckIdentitySeedTags
import com.mmg.manahub.feature.decks.domain.engine.ScoreWeights
import com.mmg.manahub.feature.decks.domain.engine.ThemeId

/**
 * The SINGLE analysis entry point (Deck Wizard Commander v3 plan, Phase 0 / E4, D2, fixes F11).
 *
 * Before this class, [com.mmg.manahub.feature.decks.domain.orchestrator.DeckDoctorOrchestrator
 * .loadAnalysis] and `app/src/test/.../harness/HarnessDoctorPipeline.evaluate` each re-derived the
 * SAME seed-inference + pin-fold sequence ("mirrors DeckDoctorOrchestrator.loadAnalysis EXACTLY",
 * per the harness's own KDoc) as two independent copies — a drift risk realized more than once in
 * this codebase's history. This class extracts that shared sequence: pick inference seed cards
 * (commander + top identity-tag mainboard cards) → run [InferDeckIdentityUseCase] → fold in the
 * deck's persisted archetype/theme/tribe pin (via [DeckIdentitySeedTags]) → call
 * [EvaluateDeckUseCase]. The orchestrator's own post-processing (resolved-card map, [AnalysisCache]
 * assembly, [com.mmg.manahub.feature.decks.domain.orchestrator.DoctorAnalysisStage] emission) and
 * the harness's own Motor A cuts/adds calls are NOT part of this pipeline — only the shared
 * seed-inference-through-evaluate core is.
 *
 * [com.mmg.manahub.feature.decks.domain.template.BuildDeckFromTemplateUseCase.recomputeProfile] is
 * NOT a mirror of this sequence (verified 2026-09-08) — it calls `DeckScorer.profile()` (the legacy
 * Motor A engine) directly and never touches [EvaluateDeckUseCase]/v3 [DeckAnalysis], so it stays
 * untouched and does not delegate here.
 */
class DeckAnalysisPipeline(
    private val evaluateDeckUseCase: EvaluateDeckUseCase,
    private val inferDeckIdentityUseCase: InferDeckIdentityUseCase,
    private val crashReporter: CrashReporter,
) {

    /**
     * @param mainboard the non-sideboard deck entries, each with a resolved [Card].
     * @param format the deck's [DeckFormat].
     * @param commander the resolved commander card, or `null` for a non-Commander deck or an
     *        unresolved commander.
     * @param archetypeOverride raw `ArchetypeId.name` from the deck's persisted pin, or
     *        null/blank/unrecognized to let the classifier infer.
     * @param themesOverride raw `ThemeId.name` strings from the deck's persisted pin.
     * @param tribeOverride the deck's persisted tribe pin, a SEPARATE column from
     *        [themesOverride] (never folded into that list's JSON).
     * @param weights the (optionally debug-tuned) legacy [ScoreWeights], forwarded to
     *        [EvaluateDeckUseCase]/`DeckScorer.evaluate`.
     * @param scoreWeightOverrides the raw (optionally debug-tuned) v3 pillar weight overrides.
     * @param sideboardCount total sideboard card count, for P5's `SideboardOversized` check.
     * @param emitProgression forwarded to [EvaluateDeckUseCase.invoke] — `false` suppresses the
     *        `ProgressionEvent.FeatureExplored` emit so a repeated wizard-internal re-evaluation
     *        (verify + refine passes) never spams the Deck Doctor exploration quest. Defaults
     *        `true`, matching every pre-existing caller's behavior.
     */
    suspend fun analyze(
        mainboard: List<DeckEntry>,
        format: DeckFormat,
        commander: Card?,
        archetypeOverride: String?,
        themesOverride: List<String>,
        tribeOverride: String? = null,
        weights: ScoreWeights = ScoreWeights(),
        scoreWeightOverrides: ScoreWeightOverrides = ScoreWeightOverrides.NONE,
        sideboardCount: Int = 0,
        emitProgression: Boolean = true,
        // Deck Wizard Commander v3 plan (E4): callers that already computed [resolveSeedTags]
        // themselves for their OWN caching purposes (see DeckDoctorOrchestrator.AnalysisCache
        // .seedTags's KDoc) can pass it here to skip a redundant re-derivation. `null` (every
        // pre-existing caller) recomputes it internally — byte-identical either way.
        precomputedSeedTags: List<CardTag>? = null,
    ): DeckHealth {
        val commanderIdentity = commander?.colorIdentity?.toSet().orEmpty()
        val commanderTags = commander?.let { it.tags + it.userTags }.orEmpty()
        val seedTags = precomputedSeedTags ?: resolveSeedTags(mainboard, commander, archetypeOverride, themesOverride, tribeOverride)

        return evaluateDeckUseCase(
            mainboard = mainboard,
            format = format,
            commanderIdentity = commanderIdentity,
            seedTags = seedTags,
            weights = weights,
            archetypeOverride = archetypeOverride,
            themesOverride = themesOverride,
            commanderTags = commanderTags,
            scoreWeightOverrides = scoreWeightOverrides,
            sideboardCount = sideboardCount,
            emitProgression = emitProgression,
        )
    }

    /**
     * The seed-tag basis [analyze] evaluates against: inference seeds (commander + top
     * identity-tag mainboard cards) through [InferDeckIdentityUseCase], plus the deck's persisted
     * archetype/theme/tribe pin folded in via [DeckIdentitySeedTags]. Exposed publicly so a caller
     * that needs to CACHE this basis for its own later incremental re-evaluation (mirrors
     * [com.mmg.manahub.feature.decks.domain.orchestrator.DeckDoctorOrchestrator.AnalysisCache
     * .seedTags]'s KDoc: reused unchanged across a session's manual card add/cuts) can compute it
     * once and pass it back into [analyze] via `precomputedSeedTags`.
     */
    suspend fun resolveSeedTags(
        mainboard: List<DeckEntry>,
        commander: Card?,
        archetypeOverride: String?,
        themesOverride: List<String>,
        tribeOverride: String? = null,
    ): List<CardTag> {
        val seedCards = inferenceSeeds(commander, mainboard)
        val inferredSeedTags = inferDeckIdentityUseCase(seedCards).seedTags
        return (inferredSeedTags + pinSeedTags(archetypeOverride, themesOverride, tribeOverride)).distinct()
    }

    /**
     * Picks the inference seed cards: the commander (when present) plus the deck's highest-weight
     * identity cards (most STRATEGY / ARCHETYPE / TRIBAL tags), capped so one off-theme card can't
     * skew the seed. Byte-identical logic to the orchestrator's pre-extraction private helper.
     */
    private fun inferenceSeeds(commander: Card?, mainboard: List<DeckEntry>): List<Card> {
        val ranked = mainboard
            .map { it.card }
            .filter { it.scryfallId != commander?.scryfallId }
            .map { card -> card to identityTagCount(card) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(MAX_SEED_CARDS)
            .map { it.first }
        return (listOfNotNull(commander) + ranked).distinctBy { it.scryfallId }
    }

    private fun identityTagCount(card: Card): Int =
        (card.tags + card.userTags).count { it.category in IDENTITY_CATEGORIES }

    /**
     * Folds a deck's PERSISTED `archetypeOverride`/`themesOverride`/`tribeOverride` pin into the
     * seed-tag basis via [DeckIdentitySeedTags] — byte-identical logic to the orchestrator's
     * pre-extraction private helper, including the stale-pin non-fatal report.
     */
    private fun pinSeedTags(archetypeOverride: String?, themesOverride: List<String>, tribeOverride: String? = null): List<CardTag> {
        val archetype = archetypeOverride?.let { name -> ArchetypeId.entries.firstOrNull { it.name == name } }
        val themes = themesOverride.mapNotNull { name -> ThemeId.entries.firstOrNull { it.name == name } }
        val archetypeStale = archetypeOverride != null && archetype == null
        val themesLostCount = themesOverride.size - themes.size
        if (archetypeStale || themesLostCount > 0) {
            crashReporter.log("deck_doctor_pin_seed_tags_unresolved")
            crashReporter.setCustomKey("deck_doctor_pin_archetype_stale", archetypeStale.toString())
            crashReporter.setCustomKey("deck_doctor_pin_themes_lost_count", themesLostCount.toString())
            crashReporter.recordException(
                RuntimeException(
                    "[DeckAnalysisPipeline] deck_doctor_pin_seed_tags_unresolved: " +
                        "archetypeOverride=$archetypeOverride themesOverride=$themesOverride"
                )
            )
        }
        if (archetype == null && themes.isEmpty() && tribeOverride.isNullOrBlank()) return emptyList()
        return DeckIdentitySeedTags.forArchetype(archetype, themes, tribeOverride)
    }

    private companion object {
        val IDENTITY_CATEGORIES = setOf(TagCategory.STRATEGY, TagCategory.ARCHETYPE, TagCategory.TRIBAL)
        const val MAX_SEED_CARDS = 8
    }
}
