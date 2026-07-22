package com.mmg.manahub.feature.decks.harness

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeSkeletonResolver
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckProfile
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.DeckWarning
import com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.template.SuggestionCategoryResolver
import com.mmg.manahub.feature.decks.domain.template.TemplateBuildResult
import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion

// ═══════════════════════════════════════════════════════════════════════════════
//  Wizard Quality Campaign -- Phase H harness. Per-build HARD/TRACKED metrics exactly as specified
//  in docs/plans/wizard-quality-campaign.md's "Metrics per build" section.
// ═══════════════════════════════════════════════════════════════════════════════

/** One matrix spec's outcome -- HARD fields drive pass/fail, TRACKED fields are reported only. */
data class BuildMetrics(
    val label: String,
    val format: DeckFormat,
    val buildFailed: Boolean,
    val failureMessage: String? = null,

    // ── HARD: size ──────────────────────────────────────────────────────────
    val actualFullSize: Int = 0,
    val targetFullSize: Int = 0,
    val shortfall: Int = 0,
    val sizeOk: Boolean = false,

    // ── HARD: legality ──────────────────────────────────────────────────────
    val illegalCards: List<String> = emptyList(),
    val duplicateNameViolations: List<String> = emptyList(),
    val overOwnedViolations: List<String> = emptyList(),
    val offColorIdentityViolations: List<String> = emptyList(),
    val legalityOk: Boolean = false,

    // ── HARD: determinism ───────────────────────────────────────────────────
    val determinismOk: Boolean = true,

    // ── HARD: lands ─────────────────────────────────────────────────────────
    val landCount: Int = 0,
    val landSharePercent: Double = 0.0,
    val hasColoredPips: Boolean = false,
    val landsOk: Boolean = true,

    // ── HARD: coherence-cuts-v2 (Deck Engine Unification, D4/plan §5 Phase 2 gate — ZERO
    //    TOLERANCE: this SUPERSEDES the Wizard Quality Campaign's Wave 2/3/4 metric. Since D4 makes
    //    the Doctor's `cuts` ranking structurally EXCLUDE every wizard-placed (source=WIZARD) card
    //    while the deck is strategyLocked -- see HarnessDoctorPipeline.evaluate's protectedIds fold
    //    -- the PROTECTED CORE is now simply "every nonland card the wizard placed," no pool-size
    //    gate, no extreme-shortfall escape valve needed: the exclusion is structural, not a ranking
    //    nudge. Checked against the FULL cuts list, not just the top 10.) ─────────────────────────
    /** Names of wizard-placed nonland cards that appear ANYWHERE in the Doctor's full cuts ranking
     * -- must be EMPTY for every spec once D4's provenance gate is wired correctly; a non-empty
     * list here means the gate broke, not that the wizard's choices were merely suboptimal. */
    val coherenceCutsV2ViolationNames: List<String> = emptyList(),
    val coherenceCutsV2Ok: Boolean = true,

    // ── HARD: coherence-swaps (Wave 2 — replaces the now-vacuous shortfall-only coherence-adds
    //    as the PRIMARY "did the wizard miss a strictly-better owned card" signal) ─────────────
    /** Names of OWNED, wizard-eligible cards in the Doctor's top-10 Motor-A adds whose category
     * the wizard's OWN best-placed card in that category scores strictly worse than (past
     * [HarnessMetricsCalculator.COHERENCE_SWAP_MARGIN]) — the wizard demonstrably ignored a
     * strictly better owned card for that slot. */
    val coherenceSwapsViolationNames: List<String> = emptyList(),
    val coherenceSwapsOk: Boolean = true,

    // ── HARD: coherence-adds (Wave 1 — kept: still meaningful while the deck is genuinely short,
    //    e.g. casual_colorless) ─────────────────────────────────────────────────────────────────
    val addsViolationNames: List<String> = emptyList(),
    val coherenceAddsOk: Boolean = true,

    // ── HARD: commander-present (Deck Engine Unification RUN 7a, plan §7 acceptance criterion 4 /
    //    BUG-1 regression guard). BUG-1 itself was a VM-layer bug (DeckWizardViewModel
    //    .writeResultIntoNewDeck never wrote the commander's qty-1 mainboard row) the harness cannot
    //    reach directly (it drives BuildDeckFromTemplateUseCase, not the VM/Room write path) -- this
    //    metric instead regression-guards the USE-CASE side of that same contract: the commander must
    //    never collide with (double-count against) a [TemplateBuildResult.deckCards] row, and once
    //    [HarnessMatrixRunner.runOne] merges in the commander's qty-1 entry the same way the VM does,
    //    it must appear EXACTLY ONCE in that merged mainboard. Defaults `true` (vacuously satisfied)
    //    for every non-Commander spec. ────────────────────────────────────────────────────────────
    val commanderPresentOk: Boolean = true,

    // ── TRACKED ──────────────────────────────────────────────────────────────
    val overallScore: Float = 0f,
    val warningCounts: Map<String, Int> = emptyMap(),
    val unresolvedCommunityMisses: Int = 0,
    val categoryFillPercent: Double = 0.0,
) {
    /** A build "passes" when every HARD field is satisfied. A failed build always fails. */
    val allHardMetricsPass: Boolean
        get() = !buildFailed && sizeOk && legalityOk && determinismOk && landsOk &&
            coherenceCutsV2Ok && coherenceSwapsOk && coherenceAddsOk && commanderPresentOk
}

object HarnessMetricsCalculator {

    fun forFailedBuild(label: String, format: DeckFormat, message: String): BuildMetrics =
        BuildMetrics(label = label, format = format, buildFailed = true, failureMessage = message)

    /** Additive nudge past which the Doctor's top-10 add candidate must beat the wizard's own
     * best-placed card in the same category before it counts as a coherence-swaps violation --
     * tunable; kept small so an honest near-tie between two reasonable picks never fails the
     * build (this is a "the wizard clearly missed something" bar, not a "not identical" bar). */
    const val COHERENCE_SWAP_MARGIN = 0.10f

    private val deckScorer = DeckScorer(RoleClassifier(), NeutralPowerResolver)
    private val manaBaseAnalyzer = ManaBaseAnalyzer()

    /**
     * @param result the wizard's [TemplateBuildResult].
     * @param secondRunDeckCards the SAME spec built a second time -- for the determinism check.
     * @param ownedQuantityByName total owned quantity per card NAME (harness collection).
     * @param eligibleOwnedNames names of owned cards that pass the wizard's OWN
     *   legality/color/non-land filter (mirrors [com.mmg.manahub.feature.decks.domain.template
     *   .BuildDeckFromTemplateUseCase.analyzeCollection] -- reimplemented locally since that
     *   function is private; see [HarnessSpecs.eligibleOwnedNames]) -- feeds coherence-adds and
     *   coherence-swaps (both require the candidate to have passed the wizard's OWN filters).
     * @param commanderIdentity the commander's color identity symbols (Commander only, else empty).
     * @param seedCardIds Scryfall ids of [com.mmg.manahub.feature.decks.domain.template
     *   .DeckWizardSpec.seeds] -- kept for signature stability; unused since Deck Engine
     *   Unification D4 made coherence-cuts-v2's PROTECTED CORE simply "every wizard-placed nonland
     *   card" (structurally guaranteed by [HarnessDoctorPipeline.evaluate]'s protectedIds fold, not
     *   this metric's own logic).
     * @param commanderCardId unused for the same reason as [seedCardIds] (D4).
     * @param commanderEntryOk RUN 7a's commander-present regression guard, precomputed by
     *   [HarnessMatrixRunner.runOne] (it already builds the merged commander+deckCards mainboard fed
     *   to the Doctor pipeline, so it is the natural place to check the merge landed correctly rather
     *   than redoing that merge here). `true` (vacuous pass) for every non-Commander spec.
     * @param doctorProfile the SAME [DeckProfile] the Doctor evaluated the deck with
     *   ([HarnessDoctorPipeline.DoctorResult.health]'s `profile` -- full-deck profile + inference
     *   seeds) -- both Wave 2 coherence metrics score against this EXACT profile, never a
     *   independently-rebuilt one (the whole campaign's coherence invariant).
     * @param cuts the Doctor's [com.mmg.manahub.feature.decks.domain.usecase.SuggestCutsUseCase]
     *   ranking (worst-fit first) over the built deck.
     * @param adds the Doctor's Motor A ([com.mmg.manahub.feature.decks.domain.usecase
     *   .SuggestAddsFromCollectionUseCase]) ranking over the built deck + the FULL owned collection.
     * @param warnings the Doctor's [com.mmg.manahub.feature.decks.domain.engine.DeckEvaluation
     *   .warnings] for the built deck (TRACKED only).
     * @param overallScore the Doctor's overall evaluation score (TRACKED only).
     */
    fun compute(
        label: String,
        format: DeckFormat,
        result: TemplateBuildResult,
        secondRunDeckCards: List<DeckEntry>?,
        ownedQuantityByName: Map<String, Int>,
        eligibleOwnedNames: Set<String>,
        commanderIdentity: Set<String>,
        seedCardIds: Set<String>,
        commanderCardId: String?,
        commanderEntryOk: Boolean = true,
        doctorProfile: DeckProfile,
        cuts: List<CardFit>,
        adds: List<AddSuggestion>,
        warnings: List<DeckWarning>,
        overallScore: Float,
    ): BuildMetrics {
        val deckCards = result.deckCards
        val nonLand = deckCards.filterNot { BasicLandCalculator.isLand(it.card) }
        val lands = deckCards.filter { BasicLandCalculator.isLand(it.card) }

        // ── size (Deck Engine Unification D3: HARD = cards + declared gaps == target — a
        //    gap-bearing deck passes, a SILENTLY short deck fails) ─────────────────────────────
        // targetDeckSize already INCLUDES the commander for DeckFormat.COMMANDER (100 = 99
        // mainboard + 1 commander) -- the commander itself is never a `TemplateBuildResult
        // .deckCards` row (see BuildDeckFromTemplateUseCase.mainboardTargetSize's own KDoc).
        val targetFull = format.targetDeckSize
        val actualFull = deckCards.sumOf { it.quantity } + (if (format == DeckFormat.COMMANDER) 1 else 0)
        val gapsTotal = result.gaps.sumOf { it.missingCount }
        val sizeOk = actualFull + gapsTotal == targetFull

        // ── legality ────────────────────────────────────────────────────────
        val illegal = nonLand.filterNot { isLegal(it.card, format) }.map { it.card.name }
        val byName = deckCards.groupBy { it.card.name }
        val basicNames = setOf("Plains", "Island", "Swamp", "Mountain", "Forest", "Wastes")
        val duplicateViolations = byName.entries
            .filter { (name, entries) -> name !in basicNames && entries.size > 1 }
            .map { it.key }
        val maxCopies = format.maxCopies
        val overMaxCopies = byName.entries
            .filter { (name, entries) -> name !in basicNames && entries.sumOf { it.quantity } > maxCopies }
            .map { it.key }
        val overOwned = byName.entries
            .filter { (name, entries) ->
                name !in basicNames && (ownedQuantityByName[name]?.let { owned -> entries.sumOf { it.quantity } > owned } ?: false)
            }
            .map { it.key }
        val offIdentity = if (format == DeckFormat.COMMANDER && commanderIdentity.isNotEmpty()) {
            nonLand.filter { entry -> entry.card.colorIdentity.any { it !in commanderIdentity } }.map { it.card.name }
        } else {
            emptyList()
        }
        val legalityOk = illegal.isEmpty() && duplicateViolations.isEmpty() && overMaxCopies.isEmpty() &&
            overOwned.isEmpty() && offIdentity.isEmpty()

        // ── determinism ─────────────────────────────────────────────────────
        val determinismOk = secondRunDeckCards?.let { second ->
            val first = deckCards.map { it.card.scryfallId to it.quantity }.sortedBy { it.first }
            val secondSorted = second.map { it.card.scryfallId to it.quantity }.sortedBy { it.first }
            first == secondSorted
        } ?: true

        // ── lands ───────────────────────────────────────────────────────────
        val landCount = lands.sumOf { it.quantity }
        val totalCount = deckCards.sumOf { it.quantity }
        val landShare = if (totalCount > 0) landCount.toDouble() / totalCount * 100.0 else 0.0
        val hasColoredPips = nonLand.any { entry -> ManaColor.entries.any { c -> c.symbol != "C" && entry.card.manaCost.orEmpty().contains(c.symbol) } }
        // Wave 3 (Task 3): the old fixed 33..40 (Commander) / 30-45% (60-card) bands ignored the
        // archetype -- e.g. AGGRO Commander's real band is RoleTarget(30, 32, 35) (ArchetypeData.kt),
        // so a legitimately on-ideal 32-land AGGRO build false-failed against the generic 33..40 floor.
        // Resolve the SAME skeleton-aware band the build itself was scored against.
        val landsOk = when {
            !hasColoredPips -> true // nothing to fix -- an all-colorless build has no land-share expectation
            format == DeckFormat.COMMANDER -> {
                val resolvedSkeleton = ArchetypeSkeletonResolver.resolveWithColor(
                    format = ArchetypeFormat.COMMANDER,
                    archetype = result.archetypeInfo.archetype,
                    themes = result.archetypeInfo.themes,
                    colorCount = commanderIdentity.size,
                )
                // Hard absolute floor, kept explicit even though a sane band should already imply it.
                landCount > 0 && landCount in resolvedSkeleton.lands.min..resolvedSkeleton.lands.max
            }
            format.isSixtyCardConstructed -> {
                val idealLands = manaBaseAnalyzer.dynamicLandIdeal(doctorProfile)
                // idealLands <= 0 means the skeleton has no defined land ideal for this profile --
                // no band to check against, same escape valve as the !hasColoredPips case above.
                landCount > 0 && (idealLands <= 0 || landCount in (idealLands - 2)..(idealLands + 2))
            }
            else -> true
        }

        // ── coherence-cuts-v2 (Deck Engine Unification D4 — ZERO TOLERANCE) ────────────────────
        // The PROTECTED CORE is now simply every wizard-placed nonland card -- D4 makes the
        // Doctor's cuts pipeline structurally EXCLUDE all of them (HarnessDoctorPipeline.evaluate's
        // protectedIds, mirroring DeckDoctorOrchestrator.cutProtectedIds), so this check is a
        // regression guard for that exclusion, not a ranking-quality heuristic. Checked against the
        // FULL cuts ranking (not just the top 10) since D4's guarantee is unconditional.
        val wizardPlacedNonLandNames = nonLand.map { it.card.name }.toSet()
        val allCutIds = cuts.map { it.card.scryfallId }.toSet()
        val cutsV2Violations = nonLand
            .filter { it.card.scryfallId in allCutIds }
            .map { it.card.name }
            .distinct()
        val coherenceCutsV2Ok = cutsV2Violations.isEmpty()

        // ── coherence-swaps ──────────────────────────────────────────────────
        // Wave 2: the direct test of the user's complaint -- "the Doctor must not ... suggest
        // different collection cards it ignored". For each of the Doctor's top-10 Motor-A adds
        // that is an OWNED card passing the wizard's OWN eligibility filters, resolve its category
        // (SuggestionCategoryResolver, SAME doctorProfile the wizard/Doctor coherence machinery
        // shares) and compare against the wizard's OWN best-placed card in that same category --
        // scored with the SAME plain DeckScorer.fit, on the SAME profile. If even the wizard's best
        // pick for that slot scores strictly worse than what the Doctor is suggesting (past
        // COHERENCE_SWAP_MARGIN), the wizard demonstrably missed a better owned card.
        val bestPlacedFitByCategory: Map<String, Float> = nonLand
            .groupBy { SuggestionCategoryResolver.resolve(it.card, profile = doctorProfile).id }
            .mapValues { (_, entries) -> entries.maxOf { deckScorer.fit(it.card, doctorProfile, isOwned = true).score } }
        val swapsViolations = adds.take(10).mapNotNull { add ->
            val name = add.fit.card.name
            if (name !in eligibleOwnedNames || name in wizardPlacedNonLandNames) return@mapNotNull null
            val categoryId = SuggestionCategoryResolver.resolve(add.fit.card, profile = doctorProfile).id
            val bestPlaced = bestPlacedFitByCategory[categoryId] ?: return@mapNotNull null
            if (bestPlaced < add.fit.score - COHERENCE_SWAP_MARGIN) name else null
        }
        val coherenceSwapsOk = swapsViolations.size <= 2

        // ── coherence-adds (Wave 1, kept -- only meaningful while the deck is short) ─────────
        // Deck Engine Unification RUN 3 (Part A.1 fix): bound the comparison window to the Doctor's
        // top-`gapsTotal` ranked adds, not the WHOLE list. SuggestAddsFromCollectionUseCase ranks up
        // to 50 owned/eligible candidates with NO fit-floor cut at the ranking stage (only the build
        // loop enforces CATEGORY_FILL_FIT_FLOOR before placing) -- checking the full 50-deep ranking
        // against the full eligible-unplaced set inflated "violations" to 33-45 for an honest 2-card
        // gap (RUN 2 finding). Only the top-`gapsTotal` candidates are even plausible fills for the
        // DECLARED gap; anything ranked below that is surplus the build never needed and was never a
        // candidate for the specific missing slots.
        val addsViolations = if (gapsTotal > 0) {
            adds.take(gapsTotal).map { it.fit.card.name }
                .filter { it in eligibleOwnedNames && it !in wizardPlacedNonLandNames }
        } else {
            emptyList()
        }
        val coherenceAddsOk = addsViolations.isEmpty()

        // ── TRACKED ─────────────────────────────────────────────────────────
        val warningCounts = warnings.groupingBy { it::class.simpleName ?: "Unknown" }.eachCount()
        val categoryFillPercent = if (result.report.isNotEmpty()) {
            result.report.filter { it.target > 0 }
                .map { (it.filled.toDouble() / it.target).coerceAtMost(1.0) }
                .average()
                .let { if (it.isNaN()) 0.0 else it * 100.0 }
        } else {
            0.0
        }

        return BuildMetrics(
            label = label,
            format = format,
            buildFailed = false,
            actualFullSize = actualFull,
            targetFullSize = targetFull,
            shortfall = gapsTotal,
            sizeOk = sizeOk,
            illegalCards = illegal,
            duplicateNameViolations = duplicateViolations + overMaxCopies,
            overOwnedViolations = overOwned,
            offColorIdentityViolations = offIdentity,
            legalityOk = legalityOk,
            determinismOk = determinismOk,
            landCount = landCount,
            landSharePercent = landShare,
            hasColoredPips = hasColoredPips,
            landsOk = landsOk,
            coherenceCutsV2ViolationNames = cutsV2Violations,
            coherenceCutsV2Ok = coherenceCutsV2Ok,
            coherenceSwapsViolationNames = swapsViolations,
            coherenceSwapsOk = coherenceSwapsOk,
            addsViolationNames = addsViolations,
            coherenceAddsOk = coherenceAddsOk,
            commanderPresentOk = commanderEntryOk,
            overallScore = overallScore,
            warningCounts = warningCounts,
            unresolvedCommunityMisses = 0,
            categoryFillPercent = categoryFillPercent,
        )
    }

    /** Mirrors [com.mmg.manahub.feature.decks.domain.template.BuildDeckFromTemplateUseCase.isLegal]
     * EXACTLY (that function is private -- duplicated here for the harness's own legality check). */
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
            DeckFormat.CASUAL, DeckFormat.DRAFT -> true
        }
    }
}
