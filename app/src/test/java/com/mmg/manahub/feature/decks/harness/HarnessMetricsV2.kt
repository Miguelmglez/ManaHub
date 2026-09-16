package com.mmg.manahub.feature.decks.harness
// COMMENTS_REVIEWED: 2026-09-16

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeData
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.CommanderPlan
import com.mmg.manahub.feature.decks.domain.engine.DeckAnalysis
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.Finding
import com.mmg.manahub.feature.decks.domain.engine.FindingSeverity
import com.mmg.manahub.feature.decks.domain.engine.LandTargetResolver
import com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.template.ManualAdd
import kotlin.math.round

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Wizard Commander v3 plan, Phase 7.1 — harness v2 metrics.
//
//  Replaces HarnessMetricsCalculator's Motor-A-era HARD set (coherence-cuts-v2/coherence-swaps/
//  coherence-adds, all scored against the retired DeckScorer/SuggestCutsUseCase pipeline) with the
//  plan §5 metric set, scored purely against WizardBuildResult + DeckAnalysis — the SAME objects
//  BuildCommanderDeckUseCase and DeckAnalysisPipeline produce. No Motor A import anywhere in this
//  file (F12's own complaint).
// ═══════════════════════════════════════════════════════════════════════════════

/** One Commander matrix spec's outcome under harness v2 — HARD fields drive pass/fail, TRACKED
 * fields are reported only (plan §5). */
data class V2BuildMetrics(
    val label: String,
    val commanderName: String,
    val strategySource: String, // "recommended" | "custom"
    val buildFailed: Boolean,
    val failureMessage: String? = null,

    // ── HARD ────────────────────────────────────────────────────────────────
    val noBlockerOk: Boolean = false,
    val sizeOrGapsOk: Boolean = false,
    val actualSize: Int = 0,
    val gapsTotal: Int = 0,
    val determinismOk: Boolean = true,
    val commanderOnceOk: Boolean = true,
    val manualAddsKeptOk: Boolean = true,
    val legalityIdentityOk: Boolean = true,
    val legalityViolations: List<String> = emptyList(),
    val identityViolations: List<String> = emptyList(),
    val noEngineAntiRoleOk: Boolean = true,
    val antiRoleViolations: List<String> = emptyList(),
    val offplanShareOk: Boolean = true,
    val offplanShare: Double = 0.0,
    val roundTripIdentityOk: Boolean = true,
    val manaSourcesOk: Boolean = true,
    val manaSourcesViolations: List<String> = emptyList(),
    val landTargetOk: Boolean = true,
    val landCount: Int = 0,
    val landBandMin: Int = 0,
    val landBandMax: Int = 0,
    /** Deck Wizard Commander v5 (S7/X5): no wizard-placed, non-fallback card pushes a role past its
     * own [com.mmg.manahub.feature.decks.domain.engine.RoleTarget.max] -- the main placement loop's
     * overflow gate hard-excludes an overflowing candidate whenever a non-overflowing alternative
     * clears the D8 floor, so this should hold by construction; a violation here means that gate
     * has a real gap. */
    val noAvoidableRoleOverflowOk: Boolean = true,
    val roleOverflowViolations: List<String> = emptyList(),
    /** Deck Wizard Commander v5 (S8/D4/X5): every card in the final analysis's "offplan" section is
     * one of [com.mmg.manahub.feature.decks.domain.template.WizardBuildResult.fallbackOffPlanIds] --
     * i.e. the wizard never slips an off-plan card in through the main loop, only through the
     * explicitly flagged last-resort fallback. */
    val noAvoidableOffplanOk: Boolean = true,
    val unflaggedOffplanCount: Int = 0,
    val fallbackStandaloneCount: Int = 0,
    val fallbackOffPlanCount: Int = 0,

    // ── TRACKED ─────────────────────────────────────────────────────────────
    val totalScore: Int = 0,
    val pillarSubscores: Map<String, Int> = emptyMap(),
    val gapSectionCount: Int = 0,
    val placedByWizard: Int = 0,
    val placedManual: Int = 0,
    val landsPlaced: Int = 0,
    val refinementSwaps: Int = 0,
    val runtimeMs: Long = 0,
    /** W7 Task 0 (7.0) re-measurement: `WizardBuildResult.ambiguityGroups.size` — now computed LIVE
     * during placement instead of after-the-fact, so this is a genuinely different (and meaningful)
     * number than W6's own after-the-fact count. */
    val ambiguityGroupCount: Int = 0,
    /** W7 Step 0: per-group `candidateIds.size` and `candidateIds.size / remainingSlots` ratio, for
     * sizing the Choice screen's per-section display cap (K). Empty when [ambiguityGroupCount] is 0. */
    val candidatesPerGroup: List<Int> = emptyList(),
    val candidateToSlotRatios: List<Double> = emptyList(),
) {
    val allHardMetricsPass: Boolean
        get() = !buildFailed && noBlockerOk && sizeOrGapsOk && determinismOk && commanderOnceOk &&
            manualAddsKeptOk && legalityIdentityOk && noEngineAntiRoleOk && offplanShareOk &&
            roundTripIdentityOk && manaSourcesOk && landTargetOk && noAvoidableRoleOverflowOk && noAvoidableOffplanOk
}

object HarnessMetricsV2Calculator {

    /** Plan §5: "offplan_share ... <= 15 % of wizard-placed non-land copies (manual adds excluded)". */
    const val OFFPLAN_SHARE_MAX = 0.15

    fun forFailedBuild(label: String, commanderName: String, strategySource: String, message: String): V2BuildMetrics =
        V2BuildMetrics(label = label, commanderName = commanderName, strategySource = strategySource, buildFailed = true, failureMessage = message)

    /**
     * @param outcome the build's own [WizardBuildResult] + resolved [CommanderPlan].
     * @param secondRunEntries the SAME spec built again — for [V2BuildMetrics.determinismOk].
     * @param roundTripAnalysis [outcome]'s own mainboard re-analyzed a second time through the
     *        SAME [com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline] call — the
     *        caller nulls `debugSynergyGraph` on both sides before comparing (pure-function
     *        determinism, not a persistence round trip — see the harness test's own KDoc for why a
     *        real Room round trip is out of this JVM-only harness's reach and already covered by
     *        `BuildCommanderDeckPersistenceRoundTripTest`).
     * @param manualAdds the spec's manual adds (empty for every real-collection/mock-rich spec this
     *        phase — kept in the signature so a future manual-add matrix slots in for free).
     * @param identity the commander's colour identity.
     * @param ownedCollection the FULL owned pool (not just what got placed) — [manaSourcesOk] needs
     *        it to tell "the collection has no fix for this splash" (declared gap, fine) apart from
     *        "the collection HAD a fix and the wizard ignored it" (a real bug).
     */
    fun compute(
        label: String,
        commanderName: String,
        strategySource: String,
        outcome: com.mmg.manahub.feature.decks.domain.template.CommanderBuildOutcome,
        secondRunEntries: List<DeckEntry>?,
        roundTripAnalysis: DeckAnalysis?,
        manualAdds: List<ManualAdd>,
        identity: Set<ManaColor>,
        ownedCollection: List<com.mmg.manahub.feature.decks.domain.template.OwnedCard>,
        runtimeMs: Long,
    ): V2BuildMetrics {
        val result = outcome.result
        val analysis = result.analysis
        val plan = outcome.plan
        val entries = result.entries
        val nonLand = entries.filterNot { BasicLandCalculator.isLand(it.card) }
        val lands = entries.filter { BasicLandCalculator.isLand(it.card) }
        val manualIds = manualAdds.map { it.card.scryfallId }.toSet()

        // ── no_blocker ──────────────────────────────────────────────────────
        val noBlockerOk = analysis.pillars.none { pillar -> pillar.findings.any { it.severity == FindingSeverity.BLOCKER } }

        // ── size_or_gaps ────────────────────────────────────────────────────
        // Plan §5: "entries + gapSections.sumOf(min - current) REACHES 100" -- a full 100-card deck
        // can still carry non-empty gapSections (a role/axis sitting below its band MINIMUM even
        // though every physical slot is filled, e.g. a generic build that simply runs light on
        // removal) -- those are advisory band deficits, not missing card slots, so they must never
        // be read as a "did the deck actually fill" signal once entries alone already reach 100.
        // ">=" (not "==") is the literal "reaches" reading: either the deck is genuinely full, or
        // the declared gaps account for whatever card-count shortfall remains.
        val actualSize = entries.sumOf { it.quantity }
        val gapsTotal = result.gapSections.sumOf { section -> (section.min ?: 0) - section.current }
        val sizeOrGapsOk = actualSize + gapsTotal >= 100

        // ── determinism ─────────────────────────────────────────────────────
        val determinismOk = secondRunEntries?.let { second ->
            val first = entries.map { it.card.scryfallId to it.quantity }.sortedBy { it.first }
            val secondSorted = second.map { it.card.scryfallId to it.quantity }.sortedBy { it.first }
            first == secondSorted
        } ?: true

        // ── commander_once ──────────────────────────────────────────────────
        // BuildCommanderDeckUseCase always constructs the commander DeckEntry as entries' FIRST
        // element (fullMainboard = listOf(commanderEntry) + placedNonLand + landEntries).
        val commanderScryfallId = outcome.result.entries.firstOrNull()?.card?.scryfallId
        val commanderOnceOk = commanderScryfallId != null &&
            entries.count { it.card.scryfallId == commanderScryfallId } == 1

        // ── manual_adds_kept ────────────────────────────────────────────────
        val manualAddsKeptOk = manualAdds.all { manual ->
            entries.any { it.card.scryfallId == manual.card.scryfallId && it.quantity >= 1 }
        }

        // ── legality_identity ───────────────────────────────────────────────
        val identitySymbols = identity.map { it.symbol }.toSet()
        val legalityViolations = nonLand.filter { it.card.legalityCommander == "banned" }.map { it.card.name }
        val identityViolations = nonLand
            .filterNot { it.card.scryfallId == commanderScryfallId }
            .filter { entry -> entry.card.colorIdentity.any { it !in identitySymbols } }
            .map { it.card.name }
        val legalityIdentityOk = legalityViolations.isEmpty() && identityViolations.isEmpty()

        // ── no_engine_anti_role ─────────────────────────────────────────────
        val antiRoles = plan.skeleton.antiRoles
        val wizardPlacedNonLand = nonLand.filterNot { it.card.scryfallId == commanderScryfallId || it.card.scryfallId in manualIds }
        val antiRoleViolations = wizardPlacedNonLand.mapNotNull { entry ->
            val roles = ArchetypeRoleClassifier.classify(entry.card)
            val dominant = roles.maxByOrNull { it.value }?.takeIf { it.value > 0f }?.key ?: return@mapNotNull null
            if (dominant in antiRoles) entry.card.name else null
        }
        val noEngineAntiRoleOk = antiRoleViolations.isEmpty()

        // ── offplan_share ───────────────────────────────────────────────────
        val offplanIds = analysis.pillars.flatMap { it.sections }
            .filter { it.id == "offplan" }
            .flatMap { section -> section.contributions.map { it.scryfallId } }
            .toSet()
        val wizardPlacedNonLandCopies = wizardPlacedNonLand.sumOf { it.quantity }
        val offplanWizardCopies = wizardPlacedNonLand.filter { it.card.scryfallId in offplanIds }.sumOf { it.quantity }
        val offplanShare = if (wizardPlacedNonLandCopies > 0) offplanWizardCopies.toDouble() / wizardPlacedNonLandCopies else 0.0
        val offplanShareOk = offplanShare <= OFFPLAN_SHARE_MAX

        // ── round_trip_identity ─────────────────────────────────────────────
        val roundTripIdentityOk = roundTripAnalysis == null ||
            analysis.copy(debugSynergyGraph = null) == roundTripAnalysis.copy(debugSynergyGraph = null)

        // ── mana_sources ────────────────────────────────────────────────────
        // A ColorSourceShortage/UnfixedSplash finding is only a WIZARD bug when the wizard had BOTH
        // an unused owned producer for that colour AND unspent non-basic-land budget left (D10's
        // own LAND_MIX cap on non-basics is a deliberate design constraint -- "don't drown a build
        // in utility lands past the archetype's basics ratio" -- not a defect; flagging every
        // capped-out build here would be a harness false positive, not a real wizard bug). Both
        // conditions recompute the SAME nonBasicCap math [BuildCommanderDeckUseCase.fillLandsV2]
        // uses internally, since that budget is private to the build loop.
        val shortageColors = analysis.pillars.flatMap { it.findings }
            .mapNotNull { f -> when (f) { is Finding.ColorSourceShortage -> f.color; is Finding.UnfixedSplash -> f.color; else -> null } }
            .toSet()
        val manaSourcesViolations = if (shortageColors.isEmpty()) {
            emptyList()
        } else {
            val placedNames = entries.map { it.card.name }.toSet()
            val archetypeFormat = ArchetypeFormat.of(DeckFormat.COMMANDER)
            val colorCount = identity.count { it != ManaColor.C }
            val landTarget = archetypeFormat?.let { LandTargetResolver.resolve(DeckFormat.COMMANDER, plan.skeleton, profile = null, manaBaseAnalyzer = ManaBaseAnalyzer()) } ?: 0
            val nonBasicPlaced = lands.filterNot { BasicLandCalculator.isBasicLand(it.card) }.sumOf { it.quantity }
            val nonBasicCap = archetypeFormat?.let { fmt ->
                val mix = ArchetypeData.landMixFor(fmt, colorCount)
                round((1.0 - (mix.basicsRatio.start + mix.basicsRatio.endInclusive) / 2.0) * landTarget).toInt()
            } ?: 0
            val budgetLeft = nonBasicPlaced < nonBasicCap

            shortageColors.mapNotNull { color ->
                val unusedProducer = ownedCollection
                    .map { it.card }
                    .filter { it.name !in placedNames }
                    .filter { identitySymbols.containsAll(it.colorIdentity) }
                    .any { card -> BasicLandCalculator.isLand(card) && !BasicLandCalculator.isBasicLand(card) && card.colorIdentity.contains(color.symbol) }
                if (unusedProducer && budgetLeft) {
                    "unused ${color.symbol} producer in collection with non-basic budget left (placed=$nonBasicPlaced cap=$nonBasicCap) while a shortage was reported"
                } else {
                    null
                }
            }
        }
        val manaSourcesOk = manaSourcesViolations.isEmpty()

        // ── land_target ─────────────────────────────────────────────────────
        val landCount = lands.sumOf { it.quantity }
        val landTargetOk = landCount in plan.skeleton.lands.min..plan.skeleton.lands.max

        // ── no_avoidable_role_overflow (S7/X5) ─────────────────────────────
        // Scoped to non-manual, non-fallback placements -- the main loop's own overflow gate hard-
        // excludes an overflowing candidate only while a non-overflowing alternative exists; a
        // fallback pick (last resort by construction, D4) is expected to sometimes overflow when
        // that is genuinely the only card left, and is not what this metric is checking.
        val fallbackIds = (result.fallbackStandaloneIds + result.fallbackOffPlanIds).toSet()
        val mainLoopPlacedNonLand = wizardPlacedNonLand.filterNot { it.card.scryfallId in fallbackIds }
        val mainLoopRoleCounts = ArchetypeRoleClassifier.deckRoleCounts(mainLoopPlacedNonLand)
        val roleOverflowViolations = plan.skeleton.roleTargets
            .filterKeys { it !in plan.skeleton.antiRoles }
            .filter { (role, target) -> (mainLoopRoleCounts[role] ?: 0) > target.max }
            .map { (role, target) -> "$role: ${mainLoopRoleCounts[role]}/${target.max}" }
        val noAvoidableRoleOverflowOk = roleOverflowViolations.isEmpty()

        // ── no_avoidable_offplan (S8/D4/X5) ────────────────────────────────
        val unflaggedOffplan = wizardPlacedNonLand
            .filter { it.card.scryfallId in offplanIds }
            .filterNot { it.card.scryfallId in result.fallbackOffPlanIds }
            .map { it.card.name }
        val noAvoidableOffplanOk = unflaggedOffplan.isEmpty()

        return V2BuildMetrics(
            label = label,
            commanderName = commanderName,
            strategySource = strategySource,
            buildFailed = false,
            noBlockerOk = noBlockerOk,
            sizeOrGapsOk = sizeOrGapsOk,
            actualSize = actualSize,
            gapsTotal = gapsTotal,
            determinismOk = determinismOk,
            commanderOnceOk = commanderOnceOk,
            manualAddsKeptOk = manualAddsKeptOk,
            legalityIdentityOk = legalityIdentityOk,
            legalityViolations = legalityViolations,
            identityViolations = identityViolations,
            noEngineAntiRoleOk = noEngineAntiRoleOk,
            antiRoleViolations = antiRoleViolations,
            offplanShareOk = offplanShareOk,
            offplanShare = offplanShare,
            roundTripIdentityOk = roundTripIdentityOk,
            manaSourcesOk = manaSourcesOk,
            manaSourcesViolations = manaSourcesViolations,
            landTargetOk = landTargetOk,
            landCount = landCount,
            landBandMin = plan.skeleton.lands.min,
            landBandMax = plan.skeleton.lands.max,
            noAvoidableRoleOverflowOk = noAvoidableRoleOverflowOk,
            roleOverflowViolations = roleOverflowViolations,
            noAvoidableOffplanOk = noAvoidableOffplanOk,
            unflaggedOffplanCount = unflaggedOffplan.size,
            fallbackStandaloneCount = result.fallbackStandaloneIds.size,
            fallbackOffPlanCount = result.fallbackOffPlanIds.size,
            totalScore = analysis.totalScore,
            pillarSubscores = analysis.pillars.associate { it.id.name to it.subscore },
            gapSectionCount = result.gapSections.size,
            placedByWizard = result.fillStats.placedByWizard,
            placedManual = result.fillStats.placedManual,
            landsPlaced = result.fillStats.lands,
            refinementSwaps = result.refinementSwaps,
            runtimeMs = runtimeMs,
            ambiguityGroupCount = result.ambiguityGroups.size,
            candidatesPerGroup = result.ambiguityGroups.map { it.candidateIds.size },
            candidateToSlotRatios = result.ambiguityGroups.map { it.candidateIds.size.toDouble() / it.remainingSlots },
        )
    }
}
