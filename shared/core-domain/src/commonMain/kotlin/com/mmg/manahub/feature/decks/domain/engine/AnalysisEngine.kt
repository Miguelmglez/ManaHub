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

    /** At most this many findings are shown per pillar before the rest collapse into an "N more"
     * count (plan §3.3's finding budget). BLOCKER findings are exempt from the cap entirely. */
    private const val FINDING_BUDGET = 3

    /** Curve-shape heuristic (P2) needs at least this many non-land copies before the histogram
     * shape check is meaningful — a 3-card preview deck has no real "shape" yet. */
    private const val MIN_SAMPLE_FOR_SHAPE = 10

    /** Legacy synergy-density floor (mirrors [DeckScorer.evaluate]'s `LowSynergyDensity` gate). */
    private const val SYNERGY_DENSITY_FLOOR = 0.35f

    /** Legacy synergy-density gate's minimum sample size (mirrors [DeckScorer.evaluate]). */
    private const val SYNERGY_DENSITY_MIN_SAMPLE = 10

    /** Tag-fingerprint alignment threshold (mirrors [DeckScorer.evaluate]'s `alignedCopies` gate). */
    private const val SYNERGY_ALIGNMENT_THRESHOLD = 0.4f

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
     */
    fun evaluate(
        mainboard: List<DeckEntry>,
        format: DeckFormat,
        colorIdentity: Set<ManaColor>,
        profile: DeckProfile,
        archetype: ArchetypeId,
        themes: List<ThemeId>,
        isManualOverride: Boolean,
        confidence: Float,
        weights: AnalysisWeights = AnalysisWeights(),
        manaBaseAnalyzer: ManaBaseAnalyzer = ManaBaseAnalyzer(),
    ): DeckAnalysis {
        val curated = CuratedStrategyCatalog.nearestFor(archetype, themes)
        val archetypeFormat = ArchetypeFormat.of(format)

        val strategyInfo = ResolvedStrategyInfo(
            curatedStrategyId = curated?.id,
            displayName = curated?.displayName
                ?: archetype.takeIf { it != ArchetypeId.GENERIC }?.displayName
                ?: "Custom",
            archetype = archetype,
            themes = themes,
            isManualOverride = isManualOverride,
            confidence = confidence,
        )

        if (archetypeFormat == null) {
            // Draft — Appendix A defines no archetype skeleton at all. P1-P4 have nothing to grade
            // against; only P5 (legality/construction, vocabulary-agnostic) still runs.
            val p5 = evaluateLegality(mainboard, format, colorIdentity)
            return compose(
                listOf(emptyPillar(PillarId.MANA_BASE), emptyPillar(PillarId.CURVE), emptyPillar(PillarId.PLAN_ROLES), emptyPillar(PillarId.SYNERGY), p5),
                strategyInfo,
                weights,
            )
        }

        val skeleton = ArchetypeSkeletonResolver.resolveWithColor(
            format = archetypeFormat,
            archetype = archetype,
            themes = themes,
            identity = colorIdentity,
        )
        val roleCounts = ArchetypeRoleClassifier.deckRoleCounts(mainboard)
        val nonLand = mainboard.filterNot { BasicLandCalculator.isLand(it.card) }
        val landCount = mainboard.filter { BasicLandCalculator.isLand(it.card) }.sumOf { it.quantity }
        val nonLandCount = nonLand.sumOf { it.quantity }
        val avgMv = if (nonLandCount == 0) 0.0 else nonLand.sumOf { it.card.cmc * it.quantity } / nonLandCount

        val p1 = evaluateManaBase(mainboard, archetypeFormat, colorIdentity, skeleton, roleCounts, landCount, avgMv, profile, manaBaseAnalyzer)
        val p2 = evaluateCurve(avgMv, nonLand, nonLandCount, skeleton, roleCounts, themes)
        val p3 = evaluatePlanRoles(skeleton, roleCounts)
        val p4 = evaluateSynergy(nonLand, nonLandCount, profile)
        val p5 = evaluateLegality(mainboard, format, colorIdentity)

        return compose(listOf(p1, p2, p3, p4, p5), strategyInfo, weights)
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
        val (shown, collapsed) = budgetFindings(findings)
        return PillarResult(id = PillarId.MANA_BASE, subscore = subscore, findings = shown, collapsedFindingsCount = collapsed)
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
        val (shown, collapsed) = budgetFindings(findings)
        return PillarResult(id = PillarId.CURVE, subscore = subscore, findings = shown, collapsedFindingsCount = collapsed)
    }

    // ── P3 — Plan roles (the "Removal 3/8" table) ──────────────────────────────────────────

    private fun evaluatePlanRoles(skeleton: ResolvedArchetypeSkeleton, roleCounts: Map<RoleKey, Int>): PillarResult {
        val findings = mutableListOf<Finding>()
        val coverage = mutableListOf<RoleCoverageEntry>()
        val ratios = mutableListOf<Float>()

        skeleton.roleTargets.forEach { (key, band) ->
            if (key == ArchetypeData.MANA_FIX_KEY) return@forEach // owned by P1, not re-shown here
            val have = roleCounts[key] ?: 0
            val label = ArchetypeRoleClassifier.label(key)
            val isAnti = key in skeleton.antiRoles
            coverage += RoleCoverageEntry(roleKey = key, label = label, current = have, min = band.min, ideal = band.ideal, max = band.max, isAntiRole = isAnti)

            if (isAnti) {
                val score = if (have <= band.max) 1f else (band.max.toFloat() / have).coerceIn(0f, 1f)
                ratios += score
                if (have > band.max) findings += Finding.AntiRoleOverMax(key, label, have, band.max)
            } else {
                ratios += bandRatioScore(have, band.ideal, band.max)
                if (have < band.min) findings += Finding.RoleGap(key, label, have, band.min)
            }
        }

        val averageRatio = if (ratios.isEmpty()) 1.0 else ratios.map { it.toDouble() }.average()
        val subscore = (100 * averageRatio).roundToInt().coerceIn(0, 100)
        val (shown, collapsed) = budgetFindings(findings)
        return PillarResult(
            id = PillarId.PLAN_ROLES,
            subscore = subscore,
            findings = shown,
            collapsedFindingsCount = collapsed,
            roleCoverage = coverage.sortedBy { it.label },
        )
    }

    // ── P4 — Synergy ────────────────────────────────────────────────────────────────────────

    /**
     * Quantity-weighted alignment against the deck's tag fingerprint — the SAME algorithm
     * [DeckScorer.evaluate]'s `synergyDensity` uses (already CardTag-key / tribe-key based, not
     * [DeckRole]-based, so nothing here needed to be "ported onto RoleKey space": the seedTags
     * pipeline and pin-folding basis are UNCHANGED, both flow in through [profile.tagFingerprint]
     * exactly as they did for the legacy engine).
     *
     * INFO: this is the weakest-calibrated of the 5 pillars — the Wave 1 calibration pass
     * (`docs/plans/deck-analysis-engine-v2-progress.md`, Phase 4 calibration entry) recorded a
     * well-built, realistic-density Azorius/UW CONTROL Commander fixture scoring SYNERGY=27 (vs.
     * MANA_BASE 84 / CURVE 94 / PLAN_ROLES 76 / LEGALITY 100 on the same deck), noting the gap as
     * "untuned tag-fingerprint alignment" and accepting it for that pass since the total still
     * landed inside the documented 65-95 "well-built, on-plan deck" band. [SYNERGY_ALIGNMENT_THRESHOLD]
     * and [SYNERGY_DENSITY_FLOOR] were carried over unchanged from the legacy engine and have not
     * been retuned against Engine v2's fixture set. Do not treat a low SYNERGY subscore alone as
     * a reliable "this deck lacks synergy" signal until a dedicated retune workstream revisits
     * this pillar.
     */
    private fun evaluateSynergy(nonLand: List<DeckEntry>, nonLandCount: Int, profile: DeckProfile): PillarResult {
        val findings = mutableListOf<Finding>()
        val alignedCopies = nonLand.sumOf { entry ->
            val tagKeys = (entry.card.tags + entry.card.userTags).map { it.key }
            val keys = tagKeys + TribeDeriver.tribeKeys(entry.card)
            val aligned = keys.any { (profile.tagFingerprint[it] ?: 0f) >= SYNERGY_ALIGNMENT_THRESHOLD }
            if (aligned) entry.quantity else 0
        }
        val density = if (nonLandCount == 0) 0f else alignedCopies.toFloat() / nonLandCount
        if (nonLandCount > SYNERGY_DENSITY_MIN_SAMPLE && density < SYNERGY_DENSITY_FLOOR) {
            findings += Finding.LowSynergyDensity(density)
        }
        val subscore = (density.coerceIn(0f, 1f) * 100).roundToInt()
        val (shown, collapsed) = budgetFindings(findings)
        return PillarResult(id = PillarId.SYNERGY, subscore = subscore, findings = shown, collapsedFindingsCount = collapsed)
    }

    // ── P5 — Legality & construction ────────────────────────────────────────────────────────

    /**
     * Ports [DeckScorer]'s private `constructionWarnings` (deck size / copy limit / off-color-
     * identity) verbatim — vocabulary-agnostic, no rewrite needed — and ADDS a genuinely new check:
     * per-card format legality (the legacy `DeckScorer.isLegal` only ever gated the `fit()`/
     * `rankAdds()` add-candidate path, it was never surfaced as a deck-level warning). Every finding
     * here is [FindingSeverity.BLOCKER] and this pillar ALSO hard-caps [DeckAnalysis.totalScore]
     * via [ILLEGAL_DECK_SCORE_CAP] — see [compose].
     */
    private fun evaluateLegality(mainboard: List<DeckEntry>, format: DeckFormat, colorIdentity: Set<ManaColor>): PillarResult {
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

        val subscore = if (findings.isEmpty()) 100 else 0
        val (shown, collapsed) = budgetFindings(findings)
        return PillarResult(id = PillarId.LEGALITY, subscore = subscore, findings = shown, collapsedFindingsCount = collapsed)
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
            DeckFormat.CASUAL -> true
            DeckFormat.DRAFT -> true
        }
    }

    // ── Composition ─────────────────────────────────────────────────────────────────────────

    private fun compose(pillars: List<PillarResult>, strategy: ResolvedStrategyInfo, weights: AnalysisWeights): DeckAnalysis {
        val w = weights.normalized()
        val byId = pillars.associateBy { it.id }
        val weighted = byId.getValue(PillarId.MANA_BASE).subscore * w.manaBase +
            byId.getValue(PillarId.CURVE).subscore * w.curve +
            byId.getValue(PillarId.PLAN_ROLES).subscore * w.planRoles +
            byId.getValue(PillarId.SYNERGY).subscore * w.synergy +
            byId.getValue(PillarId.LEGALITY).subscore * w.legality
        var total = weighted.roundToInt().coerceIn(0, 100)

        // BLOCKER findings are never collapsed by [budgetFindings] (see its own KDoc), so `findings`
        // alone is a complete view of every BLOCKER this pillar produced — no need to also inspect
        // `collapsedFindingsCount` (which only ever holds overflow WARNING/INFO findings).
        val hasLegalityBlocker = byId.getValue(PillarId.LEGALITY).findings.any { it.severity == FindingSeverity.BLOCKER }
        if (hasLegalityBlocker) total = minOf(total, ILLEGAL_DECK_SCORE_CAP)

        return DeckAnalysis(totalScore = total, pillars = pillars, strategy = strategy)
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
     * Finding budget (plan §3.3): BLOCKERs are NEVER collapsed; the remaining (non-blocker) slots
     * are filled, highest severity then largest [magnitude] first, up to [FINDING_BUDGET] total
     * shown findings. Returns the shown list plus the collapsed count.
     */
    private fun budgetFindings(all: List<Finding>): Pair<List<Finding>, Int> {
        if (all.isEmpty()) return all to 0
        val blockers = all.filter { it.severity == FindingSeverity.BLOCKER }
        val rest = all.filterNot { it.severity == FindingSeverity.BLOCKER }
            .sortedWith(compareByDescending<Finding> { severityRank(it.severity) }.thenByDescending { magnitude(it) })
        val remainingSlots = (FINDING_BUDGET - blockers.size).coerceAtLeast(0)
        val shown = blockers + rest.take(remainingSlots)
        val collapsed = (rest.size - remainingSlots).coerceAtLeast(0)
        return shown to collapsed
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
        is Finding.AntiRoleOverMax -> (finding.current - finding.max).toFloat()
        is Finding.LowSynergyDensity -> SYNERGY_DENSITY_FLOOR - finding.density
        is Finding.DeckTooSmall -> (finding.minimum - finding.current).toFloat()
        is Finding.TooManyCopies -> (finding.copies - finding.maxCopies).toFloat()
        is Finding.SingletonViolation -> finding.copies.toFloat()
        is Finding.OffColorIdentity -> 1f
        is Finding.IllegalCard -> 1f
        is Finding.UnresolvedCards -> finding.count.toFloat()
    }

    private fun Double.roundHalfUp(): Int = kotlin.math.floor(this + 0.5).toInt()
}
