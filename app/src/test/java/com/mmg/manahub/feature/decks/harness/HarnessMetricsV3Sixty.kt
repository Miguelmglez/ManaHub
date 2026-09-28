package com.mmg.manahub.feature.decks.harness
// COMMENTS_REVIEWED: 2026-09-17

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.WizardPlan
import com.mmg.manahub.feature.decks.domain.engine.DeckAnalysis
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.Finding
import com.mmg.manahub.feature.decks.domain.engine.FindingSeverity
import com.mmg.manahub.feature.decks.domain.engine.CopyPolicy
import com.mmg.manahub.feature.decks.domain.engine.isLegalForFormat

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Wizard 60-card wave (v6) plan, Phase 6.2 — the real-collection Sixty harness's HARD/TRACKED
//  metric set. Same field DEFINITIONS as HarnessMetricsV2Calculator/V2BuildMetrics (plan §5), and the
//  SAME field definitions as WizardHarnessSixtyMockSegmentsTest's own SixtySegmentMetrics
//  (commonTest, see that file's own header for why the two corpora don't share one class) --
//  generalized off the Commander-only singleton assumptions the SAME way that file's did.
// ═══════════════════════════════════════════════════════════════════════════════

/** One Sixty matrix spec's outcome under harness v1 — HARD fields drive pass/fail, TRACKED fields
 * are reported only (plan §5). */
data class V3SixtyBuildMetrics(
    val label: String,
    val format: DeckFormat,
    val strategySource: String, // "recommended" | "custom"
    val anchorKind: String, // "colors" | "cards"
    val buildFailed: Boolean,
    val failureMessage: String? = null,

    // ── HARD ────────────────────────────────────────────────────────────────
    val noBlockerOk: Boolean = false,
    val sizeOrGapsOk: Boolean = false,
    val actualSize: Int = 0,
    val gapsTotal: Int = 0,
    val determinismOk: Boolean = true,
    val quantityWithinMaxPlaceableOk: Boolean = true,
    val quantityViolations: List<String> = emptyList(),
    val legalityOk: Boolean = true,
    val legalityViolations: List<String> = emptyList(),
    val offplanShareOk: Boolean = true,
    val offplanShare: Double = 0.0,
    val noAvoidableRoleOverflowOk: Boolean = true,
    val roleOverflowViolations: List<String> = emptyList(),
    val noColorSourceShortageOk: Boolean = true,
    val landTargetOk: Boolean = true,
    val landCount: Int = 0,
    val landBandMin: Int = 0,
    val landBandMax: Int = 0,

    // ── TRACKED ─────────────────────────────────────────────────────────────
    val totalScore: Int = 0,
    val distinctNames: Int = 0,
    val fourOfCount: Int = 0,
    val runtimeMs: Long = 0,
    val resolvedMacro: String? = null,
) {
    val allHardMetricsPass: Boolean
        get() = !buildFailed && noBlockerOk && sizeOrGapsOk && determinismOk && quantityWithinMaxPlaceableOk &&
            legalityOk && offplanShareOk && noAvoidableRoleOverflowOk && noColorSourceShortageOk && landTargetOk
}

object HarnessMetricsV3SixtyCalculator {

    const val OFFPLAN_SHARE_MAX = 0.15

    fun forFailedBuild(label: String, format: DeckFormat, strategySource: String, anchorKind: String, message: String): V3SixtyBuildMetrics =
        V3SixtyBuildMetrics(label = label, format = format, strategySource = strategySource, anchorKind = anchorKind, buildFailed = true, failureMessage = message)

    fun compute(
        label: String,
        format: DeckFormat,
        strategySource: String,
        anchorKind: String,
        outcome: com.mmg.manahub.feature.decks.domain.template.CommanderBuildOutcome,
        secondRunEntries: List<DeckEntry>?,
        ownedByName: Map<String, Int>,
        runtimeMs: Long,
    ): V3SixtyBuildMetrics {
        val result = outcome.result
        val analysis = result.analysis
        val plan: WizardPlan = outcome.plan
        val entries = result.entries
        val nonLand = entries.filterNot { BasicLandCalculator.isLand(it.card) }
        val lands = entries.filter { BasicLandCalculator.isLand(it.card) }

        val noBlockerOk = analysis.pillars.none { pillar -> pillar.findings.any { it.severity == FindingSeverity.BLOCKER } }

        val actualSize = entries.sumOf { it.quantity }
        val gapsTotal = result.gapSections.sumOf { section -> (section.min ?: 0) - section.current }
        val sizeOrGapsOk = actualSize + gapsTotal >= format.targetDeckSize

        val determinismOk = secondRunEntries?.let { second ->
            val first = entries.map { it.card.scryfallId to it.quantity }.sortedBy { it.first }
            val secondSorted = second.map { it.card.scryfallId to it.quantity }.sortedBy { it.first }
            first == secondSorted
        } ?: true

        val quantityViolations = nonLand.filter { entry ->
            entry.quantity > CopyPolicy.maxPlaceable(entry.card, format, ownedByName[entry.card.name])
        }.map { "${it.card.name}=${it.quantity}" }
        val quantityWithinMaxPlaceableOk = quantityViolations.isEmpty()

        val legalityViolations = if (format == DeckFormat.CASUAL) {
            emptyList()
        } else {
            nonLand.filterNot { isLegalForFormat(it.card, format) }.map { it.card.name }
        }
        val legalityOk = legalityViolations.isEmpty()

        val offplanIds = analysis.pillars.flatMap { it.sections }.filter { it.id == "offplan" }
            .flatMap { section -> section.contributions.map { it.scryfallId } }.toSet()
        val wizardCopies = nonLand.sumOf { it.quantity }
        val offplanCopies = nonLand.filter { it.card.scryfallId in offplanIds }.sumOf { it.quantity }
        val offplanShare = if (wizardCopies > 0) offplanCopies.toDouble() / wizardCopies else 0.0
        val offplanShareOk = offplanShare <= OFFPLAN_SHARE_MAX

        val fallbackIds = (result.fallbackStandaloneIds + result.fallbackOffPlanIds).toSet()
        val mainLoopNonLand = nonLand.filterNot { it.card.scryfallId in fallbackIds }
        val mainLoopRoleCounts = ArchetypeRoleClassifier.deckRoleCounts(mainLoopNonLand)
        val roleOverflowViolations = plan.skeleton.roleTargets
            .filterKeys { it !in plan.skeleton.antiRoles }
            .filter { (role, target) -> (mainLoopRoleCounts[role] ?: 0) > target.max }
            .map { (role, target) -> "$role: ${mainLoopRoleCounts[role]}/${target.max}" }
        val noAvoidableRoleOverflowOk = roleOverflowViolations.isEmpty()

        val noColorSourceShortageOk = analysis.pillars.flatMap { it.findings }.none { it is Finding.ColorSourceShortage }

        val landCount = lands.sumOf { it.quantity }
        val landTargetOk = landCount in plan.skeleton.lands.min..plan.skeleton.lands.max

        return V3SixtyBuildMetrics(
            label = label,
            format = format,
            strategySource = strategySource,
            anchorKind = anchorKind,
            buildFailed = false,
            noBlockerOk = noBlockerOk,
            sizeOrGapsOk = sizeOrGapsOk,
            actualSize = actualSize,
            gapsTotal = gapsTotal,
            determinismOk = determinismOk,
            quantityWithinMaxPlaceableOk = quantityWithinMaxPlaceableOk,
            quantityViolations = quantityViolations,
            legalityOk = legalityOk,
            legalityViolations = legalityViolations,
            offplanShareOk = offplanShareOk,
            offplanShare = offplanShare,
            noAvoidableRoleOverflowOk = noAvoidableRoleOverflowOk,
            roleOverflowViolations = roleOverflowViolations,
            noColorSourceShortageOk = noColorSourceShortageOk,
            landTargetOk = landTargetOk,
            landCount = landCount,
            landBandMin = plan.skeleton.lands.min,
            landBandMax = plan.skeleton.lands.max,
            totalScore = analysis.totalScore,
            distinctNames = result.fillStats.distinctNames,
            fourOfCount = result.fillStats.fourOfCount,
            runtimeMs = runtimeMs,
            resolvedMacro = analysis.strategy.archetype?.name,
        )
    }
}
