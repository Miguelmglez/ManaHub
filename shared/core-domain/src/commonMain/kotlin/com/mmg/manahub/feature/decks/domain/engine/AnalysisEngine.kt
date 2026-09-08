package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import kotlin.math.abs
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════════════
//  AnalysisEngine — Deck Analysis Engine v2 plan, Phase 2 / §3.3 "Unified evaluation pipeline"
//  (the core pillar evaluators). Pure `commonMain`, zero Android imports.
//
//  ONE engine: score, role coverage, and findings ALL derive from the SAME resolved
//  [ResolvedArchetypeSkeleton] (D1). Unlike the legacy analysis path
//  ([DeckScorer.evaluate]/[ArchetypeEvaluator.evaluate]/`EvaluateDeckUseCase.applyArchetypeLayer`),
//  this pipeline runs UNCONDITIONALLY — even a plain GENERIC/no-theme deck is evaluated against its
//  own (generic) resolved skeleton (plan §6 open question 3's default: "GENERIC is itself a curated
//  'Balanced' entry"). What is REUSED, not rewritten, per the plan: [ArchetypeSkeletonResolver],
//  [ArchetypeData], [ArchetypeEvaluator.karstenLandTarget], [ManaBaseAnalyzer], the C5 construction
//  checks (ported here, vocabulary-agnostic), [ArchetypeRoleClassifier.deckRoleCounts]. What NEVER
//  runs on this path: [DeckScorer.evaluate], `ArchetypeEvaluator.evaluate`,
//  `EvaluateDeckUseCase.applyArchetypeLayer` — those stay wired ONLY for the legacy
//  [com.mmg.manahub.feature.decks.domain.usecase.DeckHealth.evaluation] display field (see
//  `EvaluateDeckUseCase`'s KDoc for why that field is kept byte-identical).
// ═══════════════════════════════════════════════════════════════════════════════

object AnalysisEngine {

    /**
     * An illegal-construction deck (any P5 BLOCKER finding — banned/off-format card, deck-too-
     * small, copy-limit/singleton violation, off-color-identity) can never score above this,
     * REGARDLESS of how well-tuned the rest of the build is (mirrors the plan's own framing: "an
     * illegal deck can't be 85"). Chosen as a documented judgment call, not a derived/cited number:
     * 40 sits below the "healthy deck" range every other pillar naturally clusters around at
     * default weights, low enough to visibly read as "broken" on the 0-100 ring, but not 0 — a
     * deck that is otherwise near-perfect and merely has ONE banned card left over from an import
     * should not look indistinguishable from an empty draft.
     *
     * ## Phase 4 calibration review (2026-08-19) — JUDGMENT CALL: NO CHANGE, flat cap kept
     * Reconsidered whether P5/legality should differentiate severity (one violation vs. many) instead
     * of this flat cap. [evaluateLegality]'s own subscore already IS binary (100 when clean, 0 the
     * moment any BLOCKER finding exists — see its own KDoc), so a graduated CAP on top of a binary
     * subscore would need an entirely separate violation-COUNT-based scale with no natural anchor
     * (is 3 illegal cards "twice as broken" as 1.5x, 2x, 3x?) and no external citation to derive one
     * from — every other calibrated number in this file traces to either a published source (Karsten,
     * Command Zone ep.658) or an explicit judgment-call comment; a graduated cap would be a SECOND,
     * uncited judgment call layered on the first. The plan's own framing ("an illegal deck can't be
     * 85") is a binary claim, not a graduated one. Kept flat, reconfirmed as a deliberate choice, not
     * an oversight — a graduated version remains a legitimate FUTURE enhancement candidate if a real
     * player-facing need for it ever surfaces (e.g. "1 easy-fix banned card" vs. "5 banned cards,
     * this deck needs a rebuild" reading very differently to a user), but is out of THIS calibration
     * pass's scope.
     */
    const val ILLEGAL_DECK_SCORE_CAP = 40

    /** [ScoreLimiter.DominantPillar] threshold (a): a pillar must cost at least this many of the
     * 100 possible points on its own before it's worth calling out — see `computeScoreLimiter`'s
     * KDoc, JUDGMENT CALL (a). */
    private const val DOMINANT_PILLAR_MIN_LOST_POINTS = 10

    /** [ScoreLimiter.DominantPillar] threshold (b): the top pillar's lost points must be at least
     * this many times the runner-up's — see `computeScoreLimiter`'s KDoc, JUDGMENT CALL (b). */
    private const val DOMINANT_PILLAR_MARGIN = 1.5f

    /** Curve-shape heuristic (P2) needs at least this many non-land copies before the histogram
     * shape check is meaningful — a 3-card preview deck has no real "shape" yet. */
    private const val MIN_SAMPLE_FOR_SHAPE = 10

    /** Max sideboard size for any 60-card constructed format
     * ([com.mmg.manahub.core.model.DeckFormat.isSixtyCardConstructed]) — the real Magic tournament
     * rule (CR 100.4a). Commander/Draft have no such cap and are never gated by this (see
     * [evaluateLegality]'s own check). Wave 2 / B3. */
    private const val MAX_SIXTY_SIDEBOARD_SIZE = 15

    /** Tag-fingerprint alignment threshold (mirrors [DeckScorer.evaluate]'s `alignedCopies` gate).
     * STILL used by [evaluateSynergy] — Deck Analysis Engine v3, PHASE 4 rescored the SUBSCORE onto
     * [SynergyGraph]/[DeckSynergyGraph] (spec §7), but deliberately kept this pre-existing
     * CardTag-fingerprint grouping for the per-key `fingerprint:<key>`/`tribe:<x>` DISPLAY sections
     * unchanged — those are a genuinely useful, independent view of the deck ("which cards pull
     * toward which named strategy") that nothing in spec §7 asks to remove. Only the OLD catch-all
     * "offplan" bucket (cards this threshold does NOT align) is now further split three ways using
     * the NEW graph-based signal — see [evaluateSynergy]'s own KDoc. */
    private const val SYNERGY_ALIGNMENT_THRESHOLD = 0.4f

    /** Deck Analysis Engine v3, PHASE 4 (spec §5.4/§7) — flat per-conflict penalty deducted from
     * P4's `raw` [0,1] composite, one instance per [SynergyConflict] the graph detects (a deck can
     * trip [SynergyConflict.OrphanProducers]/[SynergyConflict.OrphanPayoffs] on several axes at
     * once — each counts separately before the cap below). JUDGMENT CALL: the spec states the CAP
     * (−0.15) explicitly but not a per-conflict weight; a flat, uniform weight per conflict
     * (rather than ranking the 4 conflict TYPES by severity, which the spec never asks for) is the
     * simplest defensible choice — the cap already bounds the worst case regardless of how many
     * conflicts fire, so no single conflict type needs to be treated as "worse" for the penalty to
     * do its job. */
    private const val ANTI_SYNERGY_PENALTY_PER_CONFLICT = 0.05f

    /** Spec §5.4/§7: "cap at −15 raw points so a single false positive cannot tank a deck" — 0.15
     * on the `raw` [0,1] scale the pseudocode itself computes in (`raw × 100` = points). */
    private const val ANTI_SYNERGY_PENALTY_CAP = 0.15f

    /** Spec §7's exact composite weights: `0.40·coverage + 0.35·axisHealth + 0.15·connectivity +
     * 0.10·consistency`. */
    private const val SYNERGY_COVERAGE_WEIGHT = 0.40f
    private const val SYNERGY_AXIS_HEALTH_WEIGHT = 0.35f
    private const val SYNERGY_CONNECTIVITY_WEIGHT = 0.15f
    private const val SYNERGY_CONSISTENCY_WEIGHT = 0.10f

    /**
     * The unified evaluation entry point. [archetype]/[themes]/[isManualOverride]/[confidence] are
     * the caller's already-resolved strategy (pin-wins-else-inferred — the SAME resolution
     * [com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase] computes for the legacy
     * path), passed as plain engine-layer primitives rather than the usecase-layer
     * `ArchetypeResolution` type so this file has no `usecase` package dependency (domain/engine
     * stays the lower layer). [profile] is the ALREADY-computed legacy [DeckProfile] (built by
     * [DeckScorer.profile]) — reused purely as a cheap data source for P1's [ManaBaseAnalyzer] call
     * and P4's tag-fingerprint alignment (both already vocabulary-agnostic / CardTag-key based, NOT
     * [DeckRole]-based), never for scoring itself.
     *
     * @param sideboardCount total sideboard card count (Wave 2 / B3) — the analysis itself stays
     *        mainboard-only (P1-P4 never see the sideboard); this is fed to P5's
     *        [Finding.SideboardOversized] check alone. Appended LAST and defaulted so every existing
     *        call site/test keeps compiling unchanged (CLAUDE.md's "new param goes last" rule).
     * @param includeDebugSynergyGraph Deck Analysis Engine v3, PHASE 2 — debug-only opt-in (default
     *        `false`) that attaches [SynergyGraph.build]'s output to [DeckAnalysis.debugSynergyGraph].
     *        Computed AFTER [compose] returns, so it is structurally impossible for any pillar above
     *        to have read it. Appended LAST for the same reason [sideboardCount] is.
     */
    fun evaluate(
        mainboard: List<DeckEntry>,
        format: DeckFormat,
        colorIdentity: Set<ManaColor>,
        profile: DeckProfile,
        /** `null` = no confident macro (Deck Analysis Engine v3 removed `ArchetypeId.GENERIC`) —
         * an ambiguous inference or Draft's no-skeleton placeholder. The engine still grades
         * against the bare generic baseline skeleton in this case; only [ResolvedStrategyInfo
         * .displayName] degrades to "Custom"/a hybrid label (spec §2.1). */
        archetype: ArchetypeId?,
        /** Deck Analysis Engine v3 (spec §3) — the second-stage posture classifier's result, or a
         * user pin. `null` (the common case) when no posture was detected. */
        posture: PostureId? = null,
        themes: List<ThemeId>,
        isManualOverride: Boolean,
        confidence: Float,
        /** Deck Analysis Engine v3 (spec §2.1) — the macro runner-up, used ONLY to build a hybrid
         * "X / Y" display label when [confidence] falls below the ambiguity margin. `null` when
         * [archetype] is non-null (a confident/manual resolution never needs a hybrid label) or
         * when no runner-up exists. */
        runnerUpArchetype: ArchetypeId? = null,
        /** Deck Analysis Engine v3 (spec §8) — defaults to [AnalysisWeights.forMacro] keyed off THIS
         * call's own [archetype]/[themes] (live-theme count), replacing the old flat default. A
         * caller that passes an explicit [AnalysisWeights] (e.g. a [com.mmg.manahub.core.model
         * .ScoreWeightOverrides] merge, or a test that wants a fixed, controlled weight vector) still
         * wins outright — this default only applies when the caller omits the argument entirely. */
        weights: AnalysisWeights = AnalysisWeights.forMacro(archetype, themes.size),
        manaBaseAnalyzer: ManaBaseAnalyzer = ManaBaseAnalyzer(),
        sideboardCount: Int = 0,
        includeDebugSynergyGraph: Boolean = false,
    ): DeckAnalysis {
        // Ambiguous/ ambiguous-or-Draft resolutions (archetype == null) never look up a curated
        // strategy -- there is nothing confident to match against (spec §2.1: "never silently
        // coerce"). A real (possibly manually-overridden) archetype still resolves the normal way.
        val curated = archetype?.let { CuratedStrategyCatalog.nearestFor(it, themes, format, posture) }
        val archetypeFormat = ArchetypeFormat.of(format)

        val strategyInfo = ResolvedStrategyInfo(
            curatedStrategyId = curated?.id,
            displayName = when {
                curated != null -> curated.displayName
                archetype != null -> archetype.displayName
                runnerUpArchetype != null -> "Custom / ${runnerUpArchetype.displayName}"
                else -> "Custom"
            },
            archetype = archetype,
            posture = posture,
            themes = themes,
            isManualOverride = isManualOverride,
            confidence = confidence,
        )

        if (archetypeFormat == null) {
            // Draft — Appendix A defines no archetype skeleton at all. P1-P4 have nothing to grade
            // against; only P5 (legality/construction, vocabulary-agnostic) still runs.
            val p5 = evaluateLegality(mainboard, format, colorIdentity, sideboardCount)
            return compose(
                listOf(emptyPillar(PillarId.MANA_BASE), emptyPillar(PillarId.CURVE), emptyPillar(PillarId.PLAN_ROLES), emptyPillar(PillarId.SYNERGY), p5),
                strategyInfo,
                weights,
            )
        }

        val skeleton = ArchetypeSkeletonResolver.resolveWithColor(
            format = archetypeFormat,
            archetype = archetype,
            posture = posture,
            themes = themes,
            identity = colorIdentity,
            deckFormat = format,
        )
        val roleCounts = ArchetypeRoleClassifier.deckRoleCounts(mainboard)
        // Category Sections rework (W1) -- SAME classification pass as deckRoleCounts, keeps the
        // per-card attribution deckRoleCounts collapses away. See CardSection's KDoc for why this
        // list will not always sum back to roleCounts' rounded values.
        val attribution = ArchetypeRoleClassifier.deckRoleAttribution(mainboard)
        val nonLand = mainboard.filterNot { BasicLandCalculator.isLand(it.card) }
        val landCount = mainboard.filter { BasicLandCalculator.isLand(it.card) }.sumOf { it.quantity }
        val nonLandCount = nonLand.sumOf { it.quantity }
        val avgMv = if (nonLandCount == 0) 0.0 else nonLand.sumOf { it.card.cmc * it.quantity } / nonLandCount

        // Deck Analysis Engine v3, PHASE 4 -- built ONCE, unconditionally (P4 now scores off of it,
        // spec §7), and reused for the optional debug attach below. Phase 2's own "shadow mode"
        // framing (computed but read by no pillar) ends HERE for this call site; see this file's
        // and SynergyGraph's own headers. `includeEngineAxis = true` (PHASE 4b) -- this is the ONLY
        // call site that should ever see the ENGINE axis; [InferDeckArchetypeUseCase]'s own
        // independent `SynergyGraph.build` call deliberately leaves this `false` (its default) so
        // the macro resolver's "dominant axis by health" signal never sees it -- see
        // `SynergyGraph`'s own `ENGINE_AXIS` KDoc.
        val synergyGraph = SynergyGraph.build(mainboard, archetypeFormat, includeEngineAxis = true)

        val p1 = evaluateManaBase(mainboard, archetypeFormat, colorIdentity, skeleton, roleCounts, landCount, avgMv, profile, manaBaseAnalyzer, attribution)
        val p2 = evaluateCurve(avgMv, nonLand, nonLandCount, skeleton, roleCounts, themes)
        val p3 = evaluatePlanRoles(skeleton, roleCounts, attribution)
        val p4 = evaluateSynergy(mainboard, nonLand, nonLandCount, profile, archetype, themes, synergyGraph, archetypeFormat)
        val p5 = evaluateLegality(mainboard, format, colorIdentity, sideboardCount)

        val analysis = compose(listOf(p1, p2, p3, p4, p5), strategyInfo, weights)
        // Deck Analysis Engine v3, PHASE 2 -- attached AFTER compose() so no pillar above can ever
        // read it (structural guarantee, not a convention). Default-off; see DeckAnalysis's KDoc.
        return if (includeDebugSynergyGraph) {
            analysis.copy(debugSynergyGraph = synergyGraph)
        } else {
            analysis
        }
    }

    // ── P1 — Mana base ──────────────────────────────────────────────────────────────────────

    private fun evaluateManaBase(
        mainboard: List<DeckEntry>,
        archetypeFormat: ArchetypeFormat,
        colorIdentity: Set<ManaColor>,
        skeleton: ResolvedArchetypeSkeleton,
        roleCounts: Map<RoleKey, Int>,
        landCount: Int,
        avgMv: Double,
        profile: DeckProfile,
        manaBaseAnalyzer: ManaBaseAnalyzer,
        attribution: Map<RoleKey, List<CardContribution>>,
    ): PillarResult {
        val findings = mutableListOf<Finding>()

        // D18 union rule (reused verbatim): a land-count finding fires only when the count is
        // outside BOTH the skeleton's static band AND the Karsten dynamic target ± 2.
        val rampCount = roleCounts["ramp"] ?: 0
        val cardDrawCount = roleCounts["card_draw"] ?: 0
        val karstenTarget = ArchetypeEvaluator.karstenLandTarget(archetypeFormat, avgMv, rampCount, cardDrawCount)
        val inStaticBand = landCount in skeleton.lands.min..skeleton.lands.max
        val inKarstenBand = abs(landCount - karstenTarget) <= 2.0
        val landScore: Float
        if (!inStaticBand && !inKarstenBand) {
            findings += Finding.LandCountOffTarget(landCount, karstenTarget.roundHalfUp(), skeleton.lands.min, skeleton.lands.max)
            landScore = bandRatioScore(landCount, skeleton.lands.ideal, skeleton.lands.max)
        } else {
            landScore = 1f
        }

        // Per-colour fixing sources (Karsten tables) — [ManaBaseAnalyzer] reused as-is.
        val manaReport = manaBaseAnalyzer.analyze(mainboard, profile)
        manaReport.shortages.forEach { warning ->
            when (warning) {
                is DeckWarning.ColorSourceShortage -> findings += Finding.ColorSourceShortage(warning.color, warning.sources, warning.needed)
                is DeckWarning.UnfixedSplash -> findings += Finding.UnfixedSplash(warning.color)
                else -> Unit
            }
        }
        val colorFixScore = (1f - 0.2f * manaReport.shortages.size).coerceIn(0f, 1f)

        // Dedicated mana-fixing role vs the resolved colour-modulation band.
        val manaFixBand = skeleton.manaFixTarget()
        var manaFixScore = 1f
        if (manaFixBand != null) {
            val have = roleCounts[ArchetypeData.MANA_FIX_KEY] ?: 0
            if (have < manaFixBand.min) findings += Finding.ManaFixShortage(have, manaFixBand.min)
            manaFixScore = bandRatioScore(have, manaFixBand.ideal, manaFixBand.max)
        }

        // Basics-vs-fixing land composition ([ArchetypeData.LAND_MIX] — a data table that existed
        // since WS9.3 but had no consumer until this pillar).
        var landMixScore = 1f
        val colorCount = colorIdentity.size
        if (colorCount > 0 && landCount > 0) {
            val basics = mainboard
                .filter { BasicLandCalculator.isLand(it.card) && it.card.typeLine.contains("Basic", ignoreCase = true) }
                .sumOf { it.quantity }
            val basicsRatio = basics.toFloat() / landCount
            val mix = ArchetypeData.landMixFor(archetypeFormat, colorCount)
            val target = mix.basicsRatio
            if (basicsRatio < target.start || basicsRatio > target.endInclusive) {
                findings += Finding.LandMixOffTarget(basicsRatio, target.start.toFloat(), target.endInclusive.toFloat())
                val distance = if (basicsRatio < target.start) target.start - basicsRatio else basicsRatio - target.endInclusive
                landMixScore = (1f - distance.toFloat()).coerceIn(0f, 1f)
            }
        }

        val subscore = (100 * (0.4f * landScore + 0.3f * colorFixScore + 0.2f * manaFixScore + 0.1f * landMixScore)).roundToInt().coerceIn(0, 100)
        val shown = sortFindings(findings)

        // Category Sections rework (W1) -- per-color land breakdown + the mana-related role
        // buckets. "produces:X" is skipped entirely when the deck's lands produce no copies of
        // that color (plan wording: "one per color the deck's lands ACTUALLY produce") -- unlike
        // PLAN_ROLES, this is not a gap table, so an empty color is simply omitted rather than
        // shown at 0. role:ramp/role:mana_fix mirror PLAN_ROLES' section shape (id/band/
        // contributions) but live here since P1 owns mana_fix and P3 explicitly excludes it.
        val producesSections = ManaColor.entries.mapNotNull { color ->
            val symbol = color.symbol.first()
            val producing = mainboard.filter { BasicLandCalculator.isLand(it.card) && it.card.producedMana.contains(symbol) }
            val count = producing.sumOf { it.quantity }
            if (count == 0) return@mapNotNull null
            // Suggestions Tab UI Polish plan (W6, D-b): label is now a plain color name, not a raw
            // "{W}"-style token baked into the string -- the UI renders the real mana symbol
            // itself (SectionHeader's `icon` slot via a ManaSymbolImage wrapper), the way
            // CollectionGroupHeader already does for CollectionGroupingMode.COLOR. `id` is
            // UNCHANGED ("produces:X") -- SectionSearchQuery keys off `id`, never `label`.
            CardSection(
                id = "produces:${color.symbol}",
                label = color.displayName,
                current = count,
                contributions = producing.toContributions(),
            )
        }
        val rampBand = skeleton.roleTargets["ramp"]
        val rampSection = CardSection(
            id = "role:ramp",
            label = ArchetypeRoleClassifier.label("ramp"),
            current = roleCounts["ramp"] ?: 0,
            min = rampBand?.min, ideal = rampBand?.ideal, max = rampBand?.max,
            contributions = attribution["ramp"].orEmpty().collapsed(),
        )
        val manaFixSection = CardSection(
            id = "role:${ArchetypeData.MANA_FIX_KEY}",
            label = ArchetypeRoleClassifier.label(ArchetypeData.MANA_FIX_KEY),
            current = roleCounts[ArchetypeData.MANA_FIX_KEY] ?: 0,
            min = manaFixBand?.min, ideal = manaFixBand?.ideal, max = manaFixBand?.max,
            contributions = attribution[ArchetypeData.MANA_FIX_KEY].orEmpty().collapsed(),
        )
        val manaRockEntries = mainboard.filter { entry -> (entry.card.tags + entry.card.userTags).any { it.key == "mana_rock" } }
        val manaDorkEntries = mainboard.filter { entry -> (entry.card.tags + entry.card.userTags).any { it.key == "mana_dork" } }
        val manaRockSection = CardSection(
            id = "mana_rock",
            label = "Mana Rocks",
            current = manaRockEntries.sumOf { it.quantity },
            contributions = manaRockEntries.toContributions(),
        )
        val manaDorkSection = CardSection(
            id = "mana_dork",
            label = "Mana Dorks",
            current = manaDorkEntries.sumOf { it.quantity },
            contributions = manaDorkEntries.toContributions(),
        )
        val sections = producesSections + rampSection + manaFixSection + manaRockSection + manaDorkSection

        return PillarResult(id = PillarId.MANA_BASE, subscore = subscore, findings = shown, sections = sections)
    }

    // ── P2 — Curve ──────────────────────────────────────────────────────────────────────────

    private fun evaluateCurve(
        avgMv: Double,
        nonLand: List<DeckEntry>,
        nonLandCount: Int,
        skeleton: ResolvedArchetypeSkeleton,
        roleCounts: Map<RoleKey, Int>,
        themes: List<ThemeId>,
    ): PillarResult {
        val findings = mutableListOf<Finding>()

        // A.3 curve exemption (REANIMATOR_HIGH_MV) — mirrors
        // `EvaluateDeckUseCase.applyArchetypeLayer`'s gate exactly: active only while the theme is
        // resolved AND the deck clears both the graveyard_enabler and reanimation minimums.
        val exemptionActive = ThemeId.REANIMATOR in themes &&
            CurveExemption.REANIMATOR_HIGH_MV in skeleton.curveExemptions &&
            run {
                val enablerBand = skeleton.roleTargets["graveyard_enabler"]
                val reanimationBand = skeleton.roleTargets["reanimation"]
                val enablerHave = roleCounts["graveyard_enabler"] ?: 0
                val reanimationHave = roleCounts["reanimation"] ?: 0
                enablerBand != null && reanimationBand != null &&
                    enablerHave >= enablerBand.min && reanimationHave >= reanimationBand.min
            }

        var curveScore = 1f
        if (!exemptionActive) {
            val band = skeleton.curve
            curveScore = when {
                avgMv in band.min..band.max -> 1f
                avgMv < band.min -> (1.0 - (band.min - avgMv) / band.min.coerceAtLeast(0.01)).coerceIn(0.0, 1.0).toFloat()
                else -> (1.0 - (avgMv - band.max) / band.max.coerceAtLeast(0.01)).coerceIn(0.0, 1.0).toFloat()
            }
            if (avgMv !in band.min..band.max) findings += Finding.CurveOffBand(avgMv, band.min, band.max)
        }

        // Histogram vs the resolved [CurveShape] — a soft, advisory heuristic ([CurveShape] itself
        // carries no scoring contract elsewhere per its own KDoc). Buckets: low (0-2), mid (3-4),
        // high (5+), compared against what each shape would expect.
        var shapeScore = 1f
        if (nonLandCount >= MIN_SAMPLE_FOR_SHAPE) {
            val low = nonLand.filter { it.card.cmc <= 2.0 }.sumOf { it.quantity }
            val mid = nonLand.filter { it.card.cmc in 3.0..4.0 }.sumOf { it.quantity }
            val high = nonLand.filter { it.card.cmc >= 5.0 }.sumOf { it.quantity }
            val matchesShape = when (skeleton.shape) {
                CurveShape.FRONT -> low >= mid && mid >= high
                CurveShape.BELL -> mid >= low && mid >= high
                CurveShape.BACK -> high >= mid && mid >= low
            }
            if (!matchesShape) {
                findings += Finding.CurveShapeMismatch(skeleton.shape)
                shapeScore = 0.7f
            }
        }

        val subscore = (100 * (0.8f * curveScore + 0.2f * shapeScore)).roundToInt().coerceIn(0, 100)
        val shown = sortFindings(findings)

        // Category Sections rework (W1) -- mv:0..mv:6 buckets by exact integer CMC (real cards'
        // cmc is always a whole number), mv:7plus by the same `cmc >= 7.0` cut the shape heuristic
        // above already implies. No band: a curve bucket has no min/ideal/max target in this engine
        // today, only the whole-curve avg/shape checks above -- purely informational.
        // Suggestions Tab UI Polish plan (W7): "MV $mv"/"MV 7+" -> "$mv Cost"/"7+ Cost" -- plain
        // concatenation (no strings.xml format string) matches every other CardSection label built
        // in this file, none of which route through strings.xml either (the app is English-only,
        // CLAUDE.md).
        val curveSections = (0..6).map { mv ->
            val bucket = nonLand.filter { it.card.cmc.toInt() == mv }
            CardSection(
                id = "mv:$mv",
                label = "$mv Cost",
                current = bucket.sumOf { it.quantity },
                contributions = bucket.toContributions(),
            )
        } + run {
            val bucket = nonLand.filter { it.card.cmc >= 7.0 }
            CardSection(
                id = "mv:7plus",
                label = "7+ Cost",
                current = bucket.sumOf { it.quantity },
                contributions = bucket.toContributions(),
            )
        }

        return PillarResult(id = PillarId.CURVE, subscore = subscore, findings = shown, sections = curveSections)
    }

    // ── P3 — Plan roles (the "Removal 3/8" table) ──────────────────────────────────────────

    /** A single role band's [ratio] paired with its own [weight] (spec §6.3's `band.ideal.coerceAtLeast(1)`)
     * for [evaluatePlanRoles]' weighted-mean subscore. */
    private data class WeightedRoleRatio(val ratio: Float, val weight: Int)

    private fun evaluatePlanRoles(
        skeleton: ResolvedArchetypeSkeleton,
        roleCounts: Map<RoleKey, Int>,
        attribution: Map<RoleKey, List<CardContribution>>,
    ): PillarResult {
        val findings = mutableListOf<Finding>()
        val coverage = mutableListOf<RoleCoverageEntry>()
        val sections = mutableListOf<CardSection>()
        val weightedRatios = mutableListOf<WeightedRoleRatio>()

        skeleton.roleTargets.forEach { (key, band) ->
            if (key == ArchetypeData.MANA_FIX_KEY) return@forEach // owned by P1, not re-shown here
            val label = ArchetypeRoleClassifier.label(key)
            val isAnti = key in skeleton.antiRoles

            // Deck Analysis Engine v3, Phase 1 (spec §5.1) already fixed the underlying gap: a
            // "tribe_members" band (TRIBAL theme) now has a real deck-level source
            // (ArchetypeRoleClassifier.tribeMemberCount, injected into BOTH deckRoleCounts and
            // deckRoleAttribution -- see their own KDoc) -- this pillar no longer needs its own
            // special case bypassing roleCounts/attribution (Phase 3b, spec §6.4 "remove the
            // tribe_members special-case"). Every key, TRIBAL included, now reads the SAME map every
            // other consumer (SynergyGraph, Suggest*/SuggestCuts) already reads.
            val have = roleCounts[key] ?: 0
            val contributions = attribution[key].orEmpty().collapsed()

            coverage += RoleCoverageEntry(roleKey = key, label = label, current = have, min = band.min, ideal = band.ideal, max = band.max, isAntiRole = isAnti)
            sections += CardSection(id = "role:$key", label = label, current = have, min = band.min, ideal = band.ideal, max = band.max, isAntiRole = isAnti, contributions = contributions)

            val weight = band.ideal.coerceAtLeast(1)
            if (isAnti) {
                val score = if (have <= band.max) 1f else (band.max.toFloat() / have).coerceIn(0f, 1f)
                weightedRatios += WeightedRoleRatio(score, weight)
                if (have > band.max) findings += Finding.AntiRoleOverMax(key, label, have, band.max)
            } else {
                // Spec §6.1: `min == 0` means the plan does not require any of this role at all --
                // full credit at zero copies, only penalise going OVER max. `ideal` still drives the
                // RoleBelowIdeal suggestion below; it just no longer costs points silently (this hit
                // nearly every deck via GENERIC's own tutor 0-2-6 / recursion 0-2-5 bands).
                val score = if (band.min == 0) {
                    if (have <= band.max) 1f else (band.max.toFloat() / have).coerceIn(0f, 1f)
                } else {
                    bandRatioScore(have, band.ideal, band.max)
                }
                weightedRatios += WeightedRoleRatio(score, weight)
                // Spec §6.2: RoleGap (WARNING) below min, RoleBelowIdeal (INFO) in [min, ideal) --
                // mutually exclusive by construction (current can never satisfy both range checks),
                // so every point this role loses now has a visible explanation, at the right severity.
                when {
                    have < band.min -> findings += Finding.RoleGap(key, label, have, band.min)
                    have < band.ideal -> findings += Finding.RoleBelowIdeal(key, label, have, band.ideal)
                }
            }
        }

        // Spec §6.3: weighted mean (weight = band.ideal.coerceAtLeast(1)) replaces the old
        // ratios.average() -- a plan that wants 18 threat_early cares about that role more than one
        // that wants 2 recursion, and adding a theme (2-4 roles) no longer dilutes the archetype's
        // own roles down toward equal per-role weighting.
        val totalWeight = weightedRatios.sumOf { it.weight }
        val averageRatio = if (weightedRatios.isEmpty() || totalWeight <= 0) 1.0
            else weightedRatios.sumOf { it.ratio.toDouble() * it.weight } / totalWeight
        val subscore = (100 * averageRatio).roundToInt().coerceIn(0, 100)
        val shown = sortFindings(findings)
        return PillarResult(
            id = PillarId.PLAN_ROLES,
            subscore = subscore,
            findings = shown,
            roleCoverage = coverage.sortedBy { it.label },
            sections = sections.sortedBy { it.label },
        )
    }

    // ── P4 — Synergy ────────────────────────────────────────────────────────────────────────

    /** Which of the 4 "interaction" roles (spec §7: "removal / counterspell / protection —
     * legitimate, not off-plan") exempt a card from ever reading as off-plan, regardless of
     * whether it happens to sit outside the deck's synergy graph. */
    private val INTERACTION_ROLES: Set<RoleKey> = setOf("removal_spot", "removal_mass", "counterspell", "protection")

    /**
     * Deck Analysis Engine v3, PHASE 4 (spec §7) — the composite [SynergyGraph]-derived subscore,
     * replacing the old flat `100 × alignedCopies / nonLandCount` tag-fingerprint density.
     *
     * ```
     * coverage     = alignedCopies / nonLandCount          // aligned = participates in >= 1 edge
     * axisHealth   = weightedMean(health(axis) for live axes, weight = axis producer ideal)
     * connectivity = largestConnectedComponentCopies / nonLandCount
     * consistency  = hypergeometric(>= 1 producer of the dominant axis by turn 4)
     * raw          = 0.40·coverage + 0.35·axisHealth + 0.15·connectivity + 0.10·consistency
     * raw          = (raw − antiSynergyPenalty).coerceIn(0f, 1f)   // penalty capped at 0.15
     * subscore     = 100 × bandRatioScoreF(raw, idealFor(macro, themes), maxFor(macro, themes))
     * ```
     *
     * The subscore is now BAND-SHAPED and MACRO/THEME-AWARE ([coverageBandFor]): 30% coverage on a
     * pure CONTROL deck (no live theme) lands on that macro's own plateau and scores near 100,
     * while the SAME 30% on a tribal deck with a live theme scores far lower — the same raw number,
     * two different, correct verdicts (spec §7's own worked example). A LOW subscore is therefore
     * now a genuinely trustworthy "this deck's plan does not connect" signal FOR THIS DECK's OWN
     * macro/theme context — unlike the pre-Phase-4 tag-fingerprint density, which this KDoc used to
     * warn callers not to trust (see the git history of this comment for the retired warning).
     *
     * `"dominant axis"` (the `consistency` term) reuses [InferDeckArchetypeUseCase]'s own Phase-3a
     * definition (`graph.axes.maxByOrNull { it.health }`) rather than inventing a second one.
     *
     * ## Sections: what changed and what deliberately did NOT
     * The pre-existing per-key `fingerprint:<key>`/`tribe:<x>` DISPLAY sections (grouped by
     * [profile]'s CardTag-fingerprint alignment, [SYNERGY_ALIGNMENT_THRESHOLD]) are UNCHANGED —
     * nothing in spec §7 asks to remove that view, and it answers a genuinely different question
     * ("which named strategy does this card pull toward") than the graph-based subscore now does.
     * ONLY the old catch-all `"offplan"` bucket (cards the tag-fingerprint check does not align to
     * ANY key) is rebuilt as a real 3-way split, spec §7:
     *  - **`"interaction"`**: the card carries [INTERACTION_ROLES] — legitimate, not off-plan.
     *    A Swords to Plowshares must never read as off-plan (checked FIRST, unconditionally).
     *  - **`"standalone"`**: NOT interaction, but has a real reason to be in the deck: either it
     *    participates in >= 1 [DeckSynergyGraph] edge, or [ArchetypeRoleClassifier.classify] finds
     *    ANY classified role at all ("individually strong"). JUDGMENT CALL: spec §7's own prose
     *    ("individually strong, no edges") describes the common case; the exact set-complement of
     *    `"offplan"` below (has an edge OR has a role) is what makes the 3-way split exhaustive and
     *    mutually exclusive over the WHOLE residual set, including the edge case of a structurally
     *    graph-connected card (e.g. a bare Instant/Sorcery lit by [SynergyGraph]'s own type-line
     *    density producer) that never cleared the SEPARATE tag-fingerprint threshold.
     *  - **`"offplan"`**: neither — no edge, no classified role at all. What the OLD bucket should
     *    have meant all along.
     */
    private fun evaluateSynergy(
        mainboard: List<DeckEntry>,
        nonLand: List<DeckEntry>,
        nonLandCount: Int,
        profile: DeckProfile,
        macro: ArchetypeId?,
        themes: List<ThemeId>,
        graph: DeckSynergyGraph,
        format: ArchetypeFormat,
    ): PillarResult {
        // ── The graph-based signal every metric below and the offplan 3-way split share ────────
        val edgeCardIds = buildSet { graph.edges.forEach { add(it.fromCardId); add(it.toCardId) } }
        fun DeckEntry.hasEdge() = card.scryfallId in edgeCardIds

        // ── coverage ─────────────────────────────────────────────────────────────────────────
        // PHASE 4b: REVERTS phase 4's own interaction-extension deviation back to spec §7's literal
        // one-liner ("aligned = participates in >= 1 edge") — no longer needed, and now double-
        // counting. Phase 4 widened `aligned` to also count a bare interaction-role hit
        // (removal_spot/removal_mass/counterspell/protection) because those roles carried NO axis
        // membership at all, so a strictly edge-only reading measured coverage=0 for every
        // interaction-heavy deck regardless of build quality (the real UW Control fixture: 0 edges
        // out of 64 copies). That gap is now closed properly by the new ENGINE axis (see
        // SynergyGraph.buildCardProfile's own KDoc): every INTERACTION_ROLES member now AMPLIFIES
        // ENGINE, which means an interaction card in a deck that also runs real ramp/card_draw/
        // tutor producers gets a GENUINE amplifier -> producer edge — `hasEdge()` alone now correctly
        // reads true for it. The old blanket extension gave EVERY interaction card credit
        // unconditionally, even in a deck with zero producer support for it to connect to (the
        // negative fixture's own case); keeping both would double-count the exact same cards this
        // axis now earns properly through the graph, and was measured to be a major contributor to
        // phase 4's saturation problem (12/17 fixtures landing at exactly P4=100) since it inflated
        // coverage for every deck with a normal interaction suite, themed or not. See this phase's
        // gate report for the measured before/after.
        val alignedCopies = nonLand.sumOf { if (it.hasEdge()) it.quantity else 0 }
        val coverage = if (nonLandCount == 0) 0f else alignedCopies.toFloat() / nonLandCount

        // ── axisHealth ───────────────────────────────────────────────────────────────────────
        val liveAxes = graph.axes.filter { it.isLive }
        val axisHealthWeight = liveAxes.sumOf { it.producerIdeal }
        val axisHealth = if (liveAxes.isEmpty() || axisHealthWeight <= 0) 0f
            else (liveAxes.sumOf { it.health.toDouble() * it.producerIdeal } / axisHealthWeight).toFloat()

        // ── connectivity ─────────────────────────────────────────────────────────────────────
        val connectivity = if (nonLandCount == 0) 0f
            else largestConnectedComponentCopies(nonLand, graph.edges).toFloat() / nonLandCount

        // ── consistency ──────────────────────────────────────────────────────────────────────
        val dominantAxis = graph.axes.maxByOrNull { it.health }
        val deckSize = mainboard.sumOf { it.quantity }
        val consistency = dominantAxis?.let { SynergyGraph.turnFourDrawProbability(deckSize, it.producerCopies, format) } ?: 0f

        // ── anti-synergy penalty + findings (spec §5.4/§7) ──────────────────────────────────────
        val conflictFindings = graph.conflicts.map { conflict ->
            when (conflict) {
                is SynergyConflict.SelfDefeatingGraveyardHate -> Finding.SelfDefeatingGraveyardHate(conflict.graveyardHateCopies)
                is SynergyConflict.OrphanProducers -> Finding.OrphanProducers(conflict.axis, axisLabel(conflict.axis), conflict.producerCopies, conflict.producerIdeal)
                is SynergyConflict.OrphanPayoffs -> Finding.OrphanPayoffs(conflict.axis, axisLabel(conflict.axis), conflict.payoffCopies, conflict.payoffIdeal)
                is SynergyConflict.StaxVsOwnEngine -> Finding.StaxVsOwnEngine(conflict.staxPieceCopies, conflict.cardDrawCopies, conflict.controlCardDrawIdeal)
            }
        }
        val antiSynergyPenalty = (graph.conflicts.size * ANTI_SYNERGY_PENALTY_PER_CONFLICT).coerceAtMost(ANTI_SYNERGY_PENALTY_CAP)

        // ── composite + band shaping (spec §7) ──────────────────────────────────────────────────
        val raw = (
            SYNERGY_COVERAGE_WEIGHT * coverage +
                SYNERGY_AXIS_HEALTH_WEIGHT * axisHealth +
                SYNERGY_CONNECTIVITY_WEIGHT * connectivity +
                SYNERGY_CONSISTENCY_WEIGHT * consistency -
                antiSynergyPenalty
            ).coerceIn(0f, 1f)
        val band = coverageBandFor(macro, hasLiveTheme = themes.isNotEmpty())

        // Scoring-semantics fix (2026-08-27) -- "unmeasurable != zero" (see PillarResult
        // .notApplicable's own KDoc). A deck with LITERALLY ZERO graph.edges (no producer/payoff
        // pair matched on any axis, no amplifier/producer pair either) gives this pillar nothing
        // to measure -- a real, archetype-canonical deck (e.g. a themeless "removal + card
        // advantage" midrange plan) can legitimately have zero edges, and scoring it 0 asserted
        // "maximally incoherent" when the true state is "this pillar does not apply". Crisp,
        // non-tunable threshold per this run's own brief: graph.edges.isEmpty(), nothing softer --
        // deliberately NOT a minimum-edge-count threshold, which would be a new uncalibrated
        // constant. `subscore` is forced to 0 on this branch regardless of what `raw`/`band`
        // happened to compute (a formality only -- see notApplicable's KDoc for why it carries no
        // scoring weight); every other pillar/branch is untouched.
        val notApplicable = graph.edges.isEmpty()
        val subscore = if (notApplicable) 0 else (100 * bandRatioScoreF(raw, band.ideal, band.max)).roundToInt().coerceIn(0, 100)
        val shown = sortFindings(conflictFindings)

        // ── Sections: unchanged tag-fingerprint groups + the new offplan 3-way split ────────────
        data class AlignedEntry(val entry: DeckEntry, val alignedKeys: Set<String>)
        val alignedEntries = nonLand.map { entry ->
            val keys = ((entry.card.tags + entry.card.userTags).map { it.key } + TribeDeriver.tribeKeys(entry.card)).toSet()
            val alignedKeys = keys.filter { (profile.tagFingerprint[it] ?: 0f) >= SYNERGY_ALIGNMENT_THRESHOLD }.toSet()
            AlignedEntry(entry, alignedKeys)
        }
        val byKey = linkedMapOf<String, MutableList<DeckEntry>>()
        val residual = mutableListOf<DeckEntry>()
        alignedEntries.forEach { (entry, alignedKeys) ->
            if (alignedKeys.isEmpty()) residual += entry else alignedKeys.forEach { key -> byKey.getOrPut(key) { mutableListOf() } += entry }
        }
        val fingerprintSections = byKey.map { (key, entries) ->
            val isTribe = key.startsWith(TribeDeriver.TRIBE_PREFIX)
            CardSection(
                id = if (isTribe) key else "fingerprint:$key",
                label = if (isTribe) synergyTribeLabel(key) else synergyStrategyLabel(key),
                current = entries.sumOf { it.quantity },
                contributions = entries.toContributions(),
            )
        }.sortedByDescending { it.current }

        val interaction = mutableListOf<DeckEntry>()
        val standalone = mutableListOf<DeckEntry>()
        val offplan = mutableListOf<DeckEntry>()
        residual.forEach { entry ->
            val roles = ArchetypeRoleClassifier.classify(entry.card)
            when {
                roles.keys.any { it in INTERACTION_ROLES } -> interaction += entry
                roles.isNotEmpty() || entry.hasEdge() -> standalone += entry
                else -> offplan += entry
            }
        }
        fun offplanSplitSection(id: String, label: String, entries: List<DeckEntry>) = CardSection(
            id = id, label = label, current = entries.sumOf { it.quantity }, contributions = entries.toContributions(),
        )

        return PillarResult(
            id = PillarId.SYNERGY,
            subscore = subscore,
            findings = shown,
            sections = fingerprintSections + listOf(
                offplanSplitSection("interaction", "Interaction", interaction),
                offplanSplitSection("standalone", "Standalone", standalone),
                offplanSplitSection("offplan", "Off-plan", offplan),
            ),
            alignedNonLandCopies = alignedCopies,
            totalNonLandCopies = nonLandCount,
            notApplicable = notApplicable,
        )
    }

    /** Union-find over the cards that participate in >= 1 [edges] entry ONLY (both edge kinds —
     * see [DeckSynergyGraph]'s own KDoc for why a regular producer→payoff edge and an amplifier→
     * producer edge both count as real connections here) — an isolated card with NO edge is
     * deliberately excluded from the candidate node set entirely, rather than counted as its own
     * trivial size-1 "component": `connectivity` is meant to measure whether the deck's synergy
     * pieces cluster together, and counting a wholly disconnected card's own stack would let
     * `connectivity` read positive on a deck with ZERO real connections ([coverage] would
     * correctly read 0 in that same case) — a paradox this exclusion avoids by construction, so
     * `connectivity <= coverage` always holds. Returns the largest component's total COPY count
     * (quantity-weighted, mirrors every other per-axis count in this file) — 0 when [edges] is
     * empty or [nonLand] has no entry that participates in one. */
    private fun largestConnectedComponentCopies(nonLand: List<DeckEntry>, edges: List<SynergyEdge>): Int {
        if (edges.isEmpty()) return 0
        val edgeCardIds = buildSet { edges.forEach { add(it.fromCardId); add(it.toCardId) } }
        val quantityById = nonLand.filter { it.card.scryfallId in edgeCardIds }
            .groupBy { it.card.scryfallId }.mapValues { (_, group) -> group.sumOf { it.quantity } }
        if (quantityById.isEmpty()) return 0
        val parent = quantityById.keys.associateWithTo(mutableMapOf()) { it }
        fun find(x: String): String {
            var root = x
            while (parent.getValue(root) != root) root = parent.getValue(root)
            var cur = x
            while (parent.getValue(cur) != root) { val next = parent.getValue(cur); parent[cur] = root; cur = next }
            return root
        }
        fun union(a: String, b: String) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }
        edges.forEach { edge -> if (edge.fromCardId in parent && edge.toCardId in parent) union(edge.fromCardId, edge.toCardId) }
        return quantityById.entries.groupBy { find(it.key) }.maxOf { (_, members) -> members.sumOf { it.value } }
    }

    /** Spec §7's per-macro coverage band (`min-ideal-max`, all fractions of `nonLandCount` in
     * `[0,1]`), one row per [ArchetypeId] × "has a live theme or not". [min] is stored for
     * documentation/future-Finding parity with every other band in this file but, mirroring
     * [bandRatioScore]'s OWN established convention (P1's land band, P3's role bands: only `ideal`/
     * `max` shape the ratio, `min` gates a separate Finding), is NOT read by [bandRatioScoreF]
     * itself. */
    private data class CoverageBand(val min: Float, val ideal: Float, val max: Float)

    /**
     * Spec §7's coverage-bands-by-macro table. `macro == null` (an ambiguous/"Custom" resolution)
     * falls back to the MIDRANGE row — same judgment call, same rationale, as
     * [AnalysisWeights.forMacro]'s own `null` handling (spec §2.1: "MIDRANGE is the centre of the
     * space"); kept consistent with that precedent rather than inventing a second null-handling
     * rule for this pillar.
     */
    private fun coverageBandFor(macro: ArchetypeId?, hasLiveTheme: Boolean): CoverageBand = when (macro) {
        ArchetypeId.AGGRO -> if (hasLiveTheme) CoverageBand(0.40f, 0.55f, 0.85f) else CoverageBand(0.25f, 0.40f, 0.70f)
        ArchetypeId.MIDRANGE, null -> if (hasLiveTheme) CoverageBand(0.35f, 0.50f, 0.80f) else CoverageBand(0.20f, 0.35f, 0.65f)
        ArchetypeId.CONTROL -> if (hasLiveTheme) CoverageBand(0.30f, 0.45f, 0.75f) else CoverageBand(0.12f, 0.25f, 0.50f)
        ArchetypeId.COMBO -> if (hasLiveTheme) CoverageBand(0.50f, 0.70f, 0.95f) else CoverageBand(0.35f, 0.50f, 0.85f)
        ArchetypeId.PRISON -> if (hasLiveTheme) CoverageBand(0.40f, 0.55f, 0.85f) else CoverageBand(0.25f, 0.40f, 0.70f)
    }

    /** English fallback label for an [AxisKey] (mirrors [synergyStrategyLabel]/[synergyTribeLabel]'s
     * own "core-domain cannot reach TagDictionary.localize" fallback discipline). */
    private val AXIS_LABELS: Map<AxisKey, String> = mapOf(
        "LIFE" to "Life", "DEATH" to "Death", "TOKENS" to "Tokens", "COUNTERS" to "Counters",
        "LANDFALL" to "Landfall", "GRAVEYARD" to "Graveyard", "ETB" to "ETB", "SPELLS" to "Spells",
        "ARTIFACTS" to "Artifacts", "ENCHANTMENTS" to "Enchantments", "ATTACHED" to "Equipment / Auras",
        "ATTACK" to "Attack", "PLANESWALKERS" to "Planeswalkers", "GROUP" to "Group Effects",
        "MILL_OPP" to "Mill (Opponent)", "ENGINE" to "Engine", "LOCK" to "Stax Lock",
    )

    private fun axisLabel(axis: AxisKey): String = when {
        axis.startsWith("TRIBE:") -> "${axis.removePrefix("TRIBE:").replaceFirstChar { it.uppercase() }} (tribe)"
        else -> AXIS_LABELS[axis] ?: axis.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }

    /**
     * English fallback label for a SYNERGY fingerprint key (a [com.mmg.manahub.core.model.CardTag]
     * key, e.g. `"tokens"`). The plan's own W2 spec calls for `TagDictionary.localize(...)`, but
     * `TagDictionary` lives in `:shared:core-data`, which `:shared:core-domain` does NOT depend on
     * (core-domain depends only on core-model) -- reaching it here would invert the module layering.
     * This mirrors the exact same-shape fallback [com.mmg.manahub.core.model.CardTag.displayLabel]
     * already documents as "the commonMain fallback" for the identical reason (see
     * `CardTagChip.kt`'s KDoc) and [ArchetypeRoleClassifier.label]'s own `ROLE_LABELS`-miss fallback
     * a few functions up in this same file.
     */
    private fun synergyStrategyLabel(key: String): String = key.replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** `"tribe:elf"` -> `"Elf (tribe)"` (plan's exact W2 spec for tribe-key sections). */
    private fun synergyTribeLabel(tribeKey: String): String =
        "${tribeKey.removePrefix(TribeDeriver.TRIBE_PREFIX).replaceFirstChar { it.uppercase() }} (tribe)"

    // ── P5 — Legality & construction ────────────────────────────────────────────────────────

    /**
     * Ports [DeckScorer]'s private `constructionWarnings` (deck size / copy limit / off-color-
     * identity) verbatim — vocabulary-agnostic, no rewrite needed — and ADDS a genuinely new check:
     * per-card format legality (the legacy `DeckScorer.isLegal` only ever gated the `fit()`/
     * `rankAdds()` add-candidate path, it was never surfaced as a deck-level warning). Every
     * construction/legality finding here is [FindingSeverity.BLOCKER] and this pillar ALSO
     * hard-caps [DeckAnalysis.totalScore] via [ILLEGAL_DECK_SCORE_CAP] — see [compose]. The single
     * exception is [Finding.SideboardOversized] (Wave 2 / B3), which is [FindingSeverity.WARNING]
     * by design (advisory, mainboard-only analysis) and therefore does NOT zero [PillarResult
     * .subscore] or trigger the score cap on its own — see the subscore computation below.
     *
     * ## Rotation staleness caveat (Wave 2 / B3, ADR-005)
     * Per-format legality ([Card.legalityStandard] et al.) is read from the ALREADY-CACHED
     * [Card] — this pillar never makes a live Scryfall call. After a real Standard rotation, a
     * card whose cache entry has not yet been refreshed through one of the EXISTING refresh paths
     * (see `CLAUDE.md`'s "Backend call budget (ADR-005)" section) can report a stale legality
     * verdict here until that refresh happens. Per ADR-005, this pillar deliberately does NOT add a
     * new on-demand refresh path to compensate — a [Finding.IllegalCard]/legality subscore from
     * this method carries no "verified current as of right now" guarantee, only "current as of the
     * last cache refresh".
     */
    private fun evaluateLegality(mainboard: List<DeckEntry>, format: DeckFormat, colorIdentity: Set<ManaColor>, sideboardCount: Int): PillarResult {
        val findings = mutableListOf<Finding>()
        val totalCards = mainboard.sumOf { it.quantity }

        if (format != DeckFormat.CASUAL && format != DeckFormat.DRAFT && totalCards > 0) {
            val minimum = format.targetDeckSize
            if (totalCards < minimum) findings += Finding.DeckTooSmall(totalCards, minimum)
        }

        val maxCopies = format.maxCopies
        if (maxCopies in 1..98) { // 99 (Draft) == effectively unlimited -> skip
            mainboard
                .filterNot { BasicLandCalculator.isLand(it.card) && it.card.typeLine.contains("Basic", ignoreCase = true) }
                .groupBy { it.card.name }
                .forEach { (name, entries) ->
                    val copies = entries.sumOf { it.quantity }
                    if (copies > maxCopies) {
                        findings += if (format.uniqueCards) Finding.SingletonViolation(name, copies)
                        else Finding.TooManyCopies(name, copies, maxCopies)
                    }
                }
        }

        if (format == DeckFormat.COMMANDER && colorIdentity.isNotEmpty()) {
            val allowed = colorIdentity.map { it.symbol }.toSet()
            mainboard.map { it.card }.distinctBy { it.scryfallId }
                .filter { card -> card.colorIdentity.isNotEmpty() && card.colorIdentity.any { it !in allowed } }
                .forEach { findings += Finding.OffColorIdentity(it.name) }
        }

        mainboard.map { it.card }.distinctBy { it.scryfallId }
            .filterNot { isLegal(it, format) }
            .forEach { findings += Finding.IllegalCard(it.name) }

        // Wave 2 / B3: 60-card constructed only (DeckFormat.isSixtyCardConstructed — Standard,
        // Pioneer, Modern, Legacy, Vintage, Pauper, Casual). Commander/Draft are never gated (no
        // 15-card sideboard convention). Advisory WARNING, not a construction BLOCKER — deeper
        // sideboard analysis (role coverage of the board, matchup logic) is FUTURE DEBT, out of
        // scope for this pass.
        if (format.isSixtyCardConstructed && sideboardCount > MAX_SIXTY_SIDEBOARD_SIZE) {
            findings += Finding.SideboardOversized(sideboardCount)
        }

        // Binary subscore, gated on BLOCKER findings only (matches this pillar's documented
        // contract — see [ILLEGAL_DECK_SCORE_CAP]'s KDoc): 100 when clean of BLOCKERs, 0 the moment
        // any BLOCKER exists. A [Finding.SideboardOversized] WARNING alone leaves the subscore
        // (and therefore this pillar's weighted contribution to [DeckAnalysis.totalScore]) untouched
        // — it is shown as a finding but never treated as construction-breaking.
        val hasBlocker = findings.any { it.severity == FindingSeverity.BLOCKER }
        val subscore = if (hasBlocker) 0 else 100
        val shown = sortFindings(findings)

        // Category Sections rework (W1) -- legal/illegal split, grouped by scryfallId so a card
        // that (abnormally) appears as more than one DeckEntry still counts its full quantity once.
        // Always shown, even at 0 -- "Illegal 0" is itself useful confirmation.
        val entriesByLegality = mainboard.groupBy { isLegal(it.card, format) }
        fun legalitySection(legal: Boolean, id: String, label: String): CardSection {
            val entries = entriesByLegality[legal].orEmpty()
            return CardSection(
                id = id,
                label = label,
                current = entries.sumOf { it.quantity },
                contributions = entries.toContributions(),
            )
        }
        val sections = listOf(
            legalitySection(legal = true, id = "legal", label = "Legal"),
            legalitySection(legal = false, id = "illegal", label = "Illegal"),
        )

        return PillarResult(id = PillarId.LEGALITY, subscore = subscore, findings = shown, sections = sections)
    }

    /** Mirrors [DeckScorer]'s private `isLegal` verbatim ("restricted" counts as legal; CASUAL/
     * DRAFT are permissive). */
    private fun isLegal(card: Card, format: DeckFormat): Boolean {
        fun ok(s: String) = s.equals("legal", true) || s.equals("restricted", true)
        return when (format) {
            DeckFormat.STANDARD -> ok(card.legalityStandard)
            DeckFormat.PIONEER -> ok(card.legalityPioneer)
            DeckFormat.MODERN -> ok(card.legalityModern)
            DeckFormat.LEGACY -> ok(card.legalityLegacy)
            DeckFormat.VINTAGE -> ok(card.legalityVintage)
            DeckFormat.PAUPER -> ok(card.legalityPauper)
            DeckFormat.COMMANDER -> ok(card.legalityCommander)
            DeckFormat.COMMANDER_CASUAL -> true
            DeckFormat.CASUAL -> true
            DeckFormat.DRAFT -> true
        }
    }

    // ── Composition ─────────────────────────────────────────────────────────────────────────

    private fun compose(pillars: List<PillarResult>, strategy: ResolvedStrategyInfo, weights: AnalysisWeights): DeckAnalysis {
        val byId = pillars.associateBy { it.id }

        // Scoring-semantics fix (2026-08-27) -- when P4 SYNERGY is [PillarResult.notApplicable]
        // (the deck has zero synergy edges to measure), its weight is redistributed across the
        // other 4 pillars instead of silently scoring an unmeasurable pillar as 0. Reuses
        // [AnalysisWeights.normalized]'s EXISTING renormalization machinery rather than inventing a
        // parallel weighting path: zero the synergy slot first, then renormalize the rest back to
        // sum to 1 -- every remaining pillar's weight grows proportionally to fill the gap, so
        // [DeckAnalysis.totalScore] stays on the same 0-100 scale. A no-op (byte-identical to the
        // pre-fix `weights.normalized()`) whenever P4 IS applicable, which is every pre-existing
        // fixture/call site this run did not touch.
        val p4NotApplicable = byId.getValue(PillarId.SYNERGY).notApplicable
        val w = (if (p4NotApplicable) weights.copy(synergy = 0f) else weights).normalized()
        val weighted = byId.getValue(PillarId.MANA_BASE).subscore * w.manaBase +
            byId.getValue(PillarId.CURVE).subscore * w.curve +
            byId.getValue(PillarId.PLAN_ROLES).subscore * w.planRoles +
            byId.getValue(PillarId.SYNERGY).subscore * w.synergy +
            byId.getValue(PillarId.LEGALITY).subscore * w.legality
        val weightedScore = weighted.roundToInt().coerceIn(0, 100)
        var total = weightedScore

        // `findings` is always the FULL list since W4 (sortFindings only sorts, never drops), so
        // this is a complete view of every BLOCKER this pillar produced.
        val hasLegalityBlocker = byId.getValue(PillarId.LEGALITY).findings.any { it.severity == FindingSeverity.BLOCKER }
        if (hasLegalityBlocker) total = minOf(total, ILLEGAL_DECK_SCORE_CAP)

        val limiter = computeScoreLimiter(pillars = pillars, weights = w, weightedScore = weightedScore, finalScore = total)

        return DeckAnalysis(totalScore = total, pillars = pillars, strategy = strategy, limiter = limiter)
    }

    /**
     * Diagnoses [ScoreLimiter] — see that sealed interface's own KDoc for why this exists. Pure
     * read on top of [compose]'s already-final numbers; never recomputes or influences the score.
     *
     * 1. If the legality cap actually bit ([finalScore] < [weightedScore], the PRE-cap weighted
     *    total), that is unambiguously THE story — [ScoreLimiter.LegalityCapped] short-circuits
     *    before any per-pillar analysis.
     * 2. Otherwise, each pillar's own "points lost" is `round(weight_i * (100 - subscore_i))` — how
     *    many of the 100 possible points that pillar ALONE is costing at its current weight. The
     *    worst offender is a genuine dominant bottleneck (as opposed to several middling pillars
     *    none of which stands out — an ordinary "needs work across the board" deck) only when BOTH:
     *    - JUDGMENT CALL (a): it costs at least 10 of the 100 possible points on its own — below
     *      that, it isn't worth interrupting the player over.
     *    - JUDGMENT CALL (b): it costs at least 1.5x the runner-up's lost points (or there IS no
     *      runner-up — no other pillar lost anything) — below that margin there isn't really ONE
     *      story to tell, several pillars share the blame roughly evenly.
     *    Both numbers are tunable in isolation without touching the detection SHAPE above.
     */
    private fun computeScoreLimiter(
        pillars: List<PillarResult>,
        weights: AnalysisWeights,
        weightedScore: Int,
        finalScore: Int,
    ): ScoreLimiter {
        if (finalScore < weightedScore) return ScoreLimiter.LegalityCapped(uncappedScore = weightedScore)

        val weightById = mapOf(
            PillarId.MANA_BASE to weights.manaBase,
            PillarId.CURVE to weights.curve,
            PillarId.PLAN_ROLES to weights.planRoles,
            PillarId.SYNERGY to weights.synergy,
            PillarId.LEGALITY to weights.legality,
        )
        val lostPointsByPillar = pillars
            .map { it.id to (weightById.getValue(it.id) * (100 - it.subscore)).roundToInt() }
            .filter { (_, lost) -> lost > 0 }
            .sortedByDescending { (_, lost) -> lost }

        val (topId, topLost) = lostPointsByPillar.getOrNull(0) ?: return ScoreLimiter.None
        val runnerUpLost = lostPointsByPillar.getOrNull(1)?.second

        val isDominant = topLost >= DOMINANT_PILLAR_MIN_LOST_POINTS &&
            (runnerUpLost == null || topLost >= runnerUpLost * DOMINANT_PILLAR_MARGIN)
        return if (isDominant) ScoreLimiter.DominantPillar(pillarId = topId, lostPoints = topLost) else ScoreLimiter.None
    }

    private fun emptyPillar(id: PillarId): PillarResult = PillarResult(id = id, subscore = 100, findings = emptyList())

    // ── Shared scoring/budgeting helpers ───────────────────────────────────────────────────

    /**
     * Symmetric [0,1] band-fit ratio: ramps up to 1.0 as `current` reaches [ideal], stays 1.0 across
     * the healthy `[ideal, max]` band, then decays past [max] (mirrors [DeckScorer]'s private
     * `roleFitRatio`, reused conceptually rather than by direct call since that method is DeckRole-
     * typed there).
     */
    private fun bandRatioScore(current: Int, ideal: Int, max: Int): Float = when {
        ideal <= 0 -> 1f
        current <= ideal -> (current.toFloat() / ideal).coerceIn(0f, 1f)
        current <= max -> 1f
        else -> (max.toFloat() / current).coerceIn(0f, 1f)
    }

    /**
     * Deck Analysis Engine v3, PHASE 4 (spec §7) — [Float] variant of [bandRatioScore] with the
     * IDENTICAL shape (linear ramp to [ideal], flat plateau across `[ideal, max]`, hyperbolic decay
     * past [max]). P4's composite `raw` is already a `[0,1]` Float, so the Int-typed original
     * cannot be reused directly. Widened `private` -> `internal` (mirrors
     * [SectionSearchQuery.ROLE_ORACLE_FRAGMENTS]'s own precedent for exactly this reason) so a
     * dedicated `commonTest` can assert the ramp/plateau/decay shape directly, independent of the
     * full [evaluateSynergy] wiring.
     */
    internal fun bandRatioScoreF(current: Float, ideal: Float, max: Float): Float = when {
        ideal <= 0f -> 1f
        current <= ideal -> (current / ideal).coerceIn(0f, 1f)
        current <= max -> 1f
        else -> (max / current).coerceIn(0f, 1f)
    }

    /**
     * Suggestions Tab UI Polish plan (W4, D5): renamed from `budgetFindings` — used to hard-
     * truncate past a fixed cap (discarding the rest, with only a collapsed COUNT surviving into
     * [PillarResult]). Now only SORTS: BLOCKERs first, then the rest by severity then largest
     * [magnitude] — the full list is always returned. The UI (`FindingsList` in the app module)
     * owns the "show first N, then Show more/less" folding entirely client-side now that it has
     * the complete list to fold over.
     */
    private fun sortFindings(all: List<Finding>): List<Finding> {
        if (all.isEmpty()) return all
        val blockers = all.filter { it.severity == FindingSeverity.BLOCKER }
        val rest = all.filterNot { it.severity == FindingSeverity.BLOCKER }
            .sortedWith(compareByDescending<Finding> { severityRank(it.severity) }.thenByDescending { magnitude(it) })
        return blockers + rest
    }

    private fun severityRank(severity: FindingSeverity): Int = when (severity) {
        FindingSeverity.BLOCKER -> 2
        FindingSeverity.WARNING -> 1
        FindingSeverity.INFO -> 0
    }

    /** How "big" a finding's gap is, for ranking within the [FINDING_BUDGET] cap. Each branch uses
     * whatever magnitude is meaningful for that finding shape (an absolute count gap, a distance
     * from a band edge, …) — the exact scale only matters relative to other findings of the SAME
     * pillar (findings are budgeted per-pillar, never compared across pillars). */
    private fun magnitude(finding: Finding): Float = when (finding) {
        is Finding.LandCountOffTarget -> abs(finding.current - finding.karstenTarget).toFloat()
        is Finding.ColorSourceShortage -> (finding.need - finding.have).toFloat()
        is Finding.UnfixedSplash -> 100f
        is Finding.ManaFixShortage -> (finding.min - finding.current).toFloat()
        is Finding.LandMixOffTarget -> abs(finding.basicsRatio - (finding.targetMin + finding.targetMax) / 2f)
        is Finding.CurveOffBand -> abs(finding.avgMv - (finding.bandMin + finding.bandMax) / 2.0).toFloat()
        is Finding.CurveShapeMismatch -> 1f
        is Finding.RoleGap -> (finding.min - finding.current).toFloat()
        is Finding.RoleBelowIdeal -> (finding.ideal - finding.current).toFloat()
        is Finding.AntiRoleOverMax -> (finding.current - finding.max).toFloat()
        is Finding.SelfDefeatingGraveyardHate -> finding.graveyardHateCopies.toFloat()
        is Finding.OrphanProducers -> finding.producerCopies.toFloat()
        is Finding.OrphanPayoffs -> finding.payoffCopies.toFloat()
        is Finding.StaxVsOwnEngine -> finding.staxPieceCopies.toFloat()
        is Finding.DeckTooSmall -> (finding.minimum - finding.current).toFloat()
        is Finding.TooManyCopies -> (finding.copies - finding.maxCopies).toFloat()
        is Finding.SingletonViolation -> finding.copies.toFloat()
        is Finding.OffColorIdentity -> 1f
        is Finding.IllegalCard -> 1f
        is Finding.SideboardOversized -> (finding.count - MAX_SIXTY_SIDEBOARD_SIZE).toFloat()
        is Finding.UnresolvedCards -> finding.count.toFloat()
    }

    private fun Double.roundHalfUp(): Int = kotlin.math.floor(this + 0.5).toInt()

    private fun List<DeckEntry>.toContributions(): List<CardContribution> =
        groupBy { it.card.scryfallId }
            .map { (id, group) -> CardContribution(id, group.sumOf { it.quantity }, 1f) }

    /**
     * Collapses a list of contributions by `scryfallId`, summing quantities and keeping the MAX
     * confidence for each card. Category Sections rework fix (2026-08-25): a card that abnormally
     * appears multiple times in the same role bucket (or carries duplicate fingerprint tags) must
     * only produce ONE [CardContribution] so Compose LazyRow keys remain unique.
     */
    private fun List<CardContribution>.collapsed(): List<CardContribution> =
        groupBy { it.scryfallId }
            .map { (id, group) ->
                CardContribution(
                    scryfallId = id,
                    quantity   = group.sumOf { it.quantity },
                    confidence = group.maxOf { it.confidence },
                )
            }
}
