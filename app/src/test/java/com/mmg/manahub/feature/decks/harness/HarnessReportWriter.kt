package com.mmg.manahub.feature.decks.harness

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Wizard Commander v3 plan, Phase 7.1 — harness v2 report (V2BuildMetrics). Deck Wizard
//  60-card wave (v6, plan §5 Phase 7.1): the Motor-A-era v1 report (HarnessReportRow/HarnessReport/
//  BuildMetrics.toReportRow/HarnessReportWriter's old write() overload) that used to precede this
//  block was deleted here, along with WizardQualityMatrixTest/HarnessMatrixRunner, its only callers.
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class HarnessReportRowV2(
    val label: String,
    @SerialName("commander_name") val commanderName: String,
    @SerialName("strategy_source") val strategySource: String,
    @SerialName("build_failed") val buildFailed: Boolean,
    @SerialName("failure_message") val failureMessage: String? = null,
    @SerialName("no_blocker_ok") val noBlockerOk: Boolean,
    @SerialName("size_or_gaps_ok") val sizeOrGapsOk: Boolean,
    @SerialName("actual_size") val actualSize: Int,
    @SerialName("gaps_total") val gapsTotal: Int,
    @SerialName("determinism_ok") val determinismOk: Boolean,
    @SerialName("commander_once_ok") val commanderOnceOk: Boolean,
    @SerialName("manual_adds_kept_ok") val manualAddsKeptOk: Boolean,
    @SerialName("legality_identity_ok") val legalityIdentityOk: Boolean,
    @SerialName("no_engine_anti_role_ok") val noEngineAntiRoleOk: Boolean,
    @SerialName("anti_role_violations") val antiRoleViolations: List<String>,
    @SerialName("offplan_share_ok") val offplanShareOk: Boolean,
    @SerialName("offplan_share") val offplanShare: Double,
    @SerialName("round_trip_identity_ok") val roundTripIdentityOk: Boolean,
    @SerialName("mana_sources_ok") val manaSourcesOk: Boolean,
    @SerialName("mana_sources_violations") val manaSourcesViolations: List<String>,
    @SerialName("land_target_ok") val landTargetOk: Boolean,
    @SerialName("land_count") val landCount: Int,
    @SerialName("all_hard_metrics_pass") val allHardMetricsPass: Boolean,
    @SerialName("total_score") val totalScore: Int,
    @SerialName("pillar_subscores") val pillarSubscores: Map<String, Int>,
    @SerialName("gap_section_count") val gapSectionCount: Int,
    @SerialName("placed_by_wizard") val placedByWizard: Int,
    @SerialName("placed_manual") val placedManual: Int,
    @SerialName("lands_placed") val landsPlaced: Int,
    @SerialName("refinement_swaps") val refinementSwaps: Int,
    @SerialName("runtime_ms") val runtimeMs: Long,
)

@Serializable
data class HarnessReportV2(
    @SerialName("run_id") val runId: String,
    val label: String,
    val segment: String,
    @SerialName("timestamp_ms") val timestampMs: Long,
    @SerialName("total_specs") val totalSpecs: Int,
    @SerialName("passed_specs") val passedSpecs: Int,
    val rows: List<HarnessReportRowV2>,
)

fun V2BuildMetrics.toReportRowV2(): HarnessReportRowV2 = HarnessReportRowV2(
    label = label,
    commanderName = commanderName,
    strategySource = strategySource,
    buildFailed = buildFailed,
    failureMessage = failureMessage,
    noBlockerOk = noBlockerOk,
    sizeOrGapsOk = sizeOrGapsOk,
    actualSize = actualSize,
    gapsTotal = gapsTotal,
    determinismOk = determinismOk,
    commanderOnceOk = commanderOnceOk,
    manualAddsKeptOk = manualAddsKeptOk,
    legalityIdentityOk = legalityIdentityOk,
    noEngineAntiRoleOk = noEngineAntiRoleOk,
    antiRoleViolations = antiRoleViolations,
    offplanShareOk = offplanShareOk,
    offplanShare = offplanShare,
    roundTripIdentityOk = roundTripIdentityOk,
    manaSourcesOk = manaSourcesOk,
    manaSourcesViolations = manaSourcesViolations,
    landTargetOk = landTargetOk,
    landCount = landCount,
    allHardMetricsPass = allHardMetricsPass,
    totalScore = totalScore,
    pillarSubscores = pillarSubscores,
    gapSectionCount = gapSectionCount,
    placedByWizard = placedByWizard,
    placedManual = placedManual,
    landsPlaced = landsPlaced,
    refinementSwaps = refinementSwaps,
    runtimeMs = runtimeMs,
)

object HarnessReportWriterV2 {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun write(harnessDir: File, runId: String, label: String, segment: String, metrics: List<V2BuildMetrics>) {
        val reportDir = File(harnessDir, "reports/$runId")
        reportDir.mkdirs()

        val rows = metrics.map { it.toReportRowV2() }
        val report = HarnessReportV2(
            runId = runId,
            label = label,
            segment = segment,
            timestampMs = System.currentTimeMillis(),
            totalSpecs = rows.size,
            passedSpecs = rows.count { it.allHardMetricsPass },
            rows = rows,
        )
        File(reportDir, "report.json").writeText(json.encodeToString(HarnessReportV2.serializer(), report))
        File(reportDir, "report.md").writeText(renderMarkdown(report))
    }

    private fun renderMarkdown(report: HarnessReportV2): String = buildString {
        appendLine("# Deck Wizard Commander v3 harness v2 report — `${report.segment}` (${report.label})")
        appendLine()
        appendLine("Run id: `${report.runId}`  ")
        appendLine("Specs: ${report.totalSpecs}  ")
        appendLine("Passed (all HARD metrics): ${report.passedSpecs} / ${report.totalSpecs}")
        appendLine()

        appendLine("## Aggregate HARD-metric pass counts")
        appendLine()
        appendLine("| Metric | Pass | Fail |")
        appendLine("|---|---|---|")
        val nonFailed = report.rows.filterNot { it.buildFailed }
        appendLine(hardMetricRow("build succeeded", report.rows.count { !it.buildFailed }, report.rows.count { it.buildFailed }))
        appendLine(hardMetricRow("no_blocker", nonFailed.count { it.noBlockerOk }, nonFailed.count { !it.noBlockerOk }))
        appendLine(hardMetricRow("size_or_gaps", nonFailed.count { it.sizeOrGapsOk }, nonFailed.count { !it.sizeOrGapsOk }))
        appendLine(hardMetricRow("determinism", nonFailed.count { it.determinismOk }, nonFailed.count { !it.determinismOk }))
        appendLine(hardMetricRow("commander_once", nonFailed.count { it.commanderOnceOk }, nonFailed.count { !it.commanderOnceOk }))
        appendLine(hardMetricRow("manual_adds_kept", nonFailed.count { it.manualAddsKeptOk }, nonFailed.count { !it.manualAddsKeptOk }))
        appendLine(hardMetricRow("legality_identity", nonFailed.count { it.legalityIdentityOk }, nonFailed.count { !it.legalityIdentityOk }))
        appendLine(hardMetricRow("no_engine_anti_role", nonFailed.count { it.noEngineAntiRoleOk }, nonFailed.count { !it.noEngineAntiRoleOk }))
        appendLine(hardMetricRow("offplan_share", nonFailed.count { it.offplanShareOk }, nonFailed.count { !it.offplanShareOk }))
        appendLine(hardMetricRow("round_trip_identity", nonFailed.count { it.roundTripIdentityOk }, nonFailed.count { !it.roundTripIdentityOk }))
        appendLine(hardMetricRow("mana_sources", nonFailed.count { it.manaSourcesOk }, nonFailed.count { !it.manaSourcesOk }))
        appendLine(hardMetricRow("land_target", nonFailed.count { it.landTargetOk }, nonFailed.count { !it.landTargetOk }))
        appendLine()

        if (nonFailed.isNotEmpty()) {
            val scores = nonFailed.map { it.totalScore }.sorted()
            appendLine("## TRACKED — total score distribution")
            appendLine()
            appendLine("min=${scores.first()} median=${scores[scores.size / 2]} max=${scores.last()} mean=${"%.1f".format(scores.average())}")
            appendLine()
        }

        appendLine("## Worst offenders (first 20 failing specs)")
        appendLine()
        appendLine("| Spec | Fail reasons |")
        appendLine("|---|---|")
        report.rows.filterNot { it.allHardMetricsPass }.take(20).forEach { row ->
            appendLine("| ${row.label} | ${failReasonsV2(row)} |")
        }
    }

    private fun hardMetricRow(name: String, pass: Int, fail: Int): String = "| $name | $pass | $fail |"

    private fun failReasonsV2(row: HarnessReportRowV2): String {
        if (row.buildFailed) return "build failed: ${row.failureMessage}"
        val reasons = mutableListOf<String>()
        if (!row.noBlockerOk) reasons += "blocker"
        if (!row.sizeOrGapsOk) reasons += "size_or_gaps(${row.actualSize}+${row.gapsTotal})"
        if (!row.determinismOk) reasons += "determinism"
        if (!row.commanderOnceOk) reasons += "commander_once"
        if (!row.manualAddsKeptOk) reasons += "manual_adds_kept"
        if (!row.legalityIdentityOk) reasons += "legality_identity"
        if (!row.noEngineAntiRoleOk) reasons += "anti_role(${row.antiRoleViolations.joinToString(",")})"
        if (!row.offplanShareOk) reasons += "offplan_share(${"%.2f".format(row.offplanShare)})"
        if (!row.roundTripIdentityOk) reasons += "round_trip_identity"
        if (!row.manaSourcesOk) reasons += "mana_sources(${row.manaSourcesViolations.joinToString(",")})"
        if (!row.landTargetOk) reasons += "land_target(${row.landCount})"
        return reasons.joinToString(", ")
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Wizard 60-card wave (v6) plan, Phase 6.2 — harness v1's real-collection Sixty-format report.
//  A THIRD, separate row/report shape (same rationale as HarnessReportWriterV2's own header: the
//  metric vocabulary generalizes HarnessReportRowV2 off the Commander-only singleton assumptions --
//  no commander_once/manual_adds_kept/mana_sources/round_trip fields, adds quantity/legality/
//  role-overflow/color-source-shortage fields V3SixtyBuildMetrics carries instead).
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class HarnessReportRowV3Sixty(
    val label: String,
    val format: String,
    @SerialName("anchor_kind") val anchorKind: String,
    @SerialName("strategy_source") val strategySource: String,
    @SerialName("build_failed") val buildFailed: Boolean,
    @SerialName("failure_message") val failureMessage: String? = null,
    @SerialName("no_blocker_ok") val noBlockerOk: Boolean,
    @SerialName("size_or_gaps_ok") val sizeOrGapsOk: Boolean,
    @SerialName("actual_size") val actualSize: Int,
    @SerialName("gaps_total") val gapsTotal: Int,
    @SerialName("determinism_ok") val determinismOk: Boolean,
    @SerialName("quantity_within_max_placeable_ok") val quantityWithinMaxPlaceableOk: Boolean,
    @SerialName("quantity_violations") val quantityViolations: List<String>,
    @SerialName("legality_ok") val legalityOk: Boolean,
    @SerialName("legality_violations") val legalityViolations: List<String>,
    @SerialName("offplan_share_ok") val offplanShareOk: Boolean,
    @SerialName("offplan_share") val offplanShare: Double,
    @SerialName("no_avoidable_role_overflow_ok") val noAvoidableRoleOverflowOk: Boolean,
    @SerialName("role_overflow_violations") val roleOverflowViolations: List<String>,
    @SerialName("no_color_source_shortage_ok") val noColorSourceShortageOk: Boolean,
    @SerialName("land_target_ok") val landTargetOk: Boolean,
    @SerialName("land_count") val landCount: Int,
    @SerialName("all_hard_metrics_pass") val allHardMetricsPass: Boolean,
    @SerialName("total_score") val totalScore: Int,
    @SerialName("distinct_names") val distinctNames: Int,
    @SerialName("four_of_count") val fourOfCount: Int,
    @SerialName("runtime_ms") val runtimeMs: Long,
    @SerialName("resolved_macro") val resolvedMacro: String? = null,
)

@Serializable
data class HarnessReportV3Sixty(
    @SerialName("run_id") val runId: String,
    val label: String,
    @SerialName("timestamp_ms") val timestampMs: Long,
    @SerialName("total_specs") val totalSpecs: Int,
    @SerialName("passed_specs") val passedSpecs: Int,
    val rows: List<HarnessReportRowV3Sixty>,
)

fun V3SixtyBuildMetrics.toReportRowV3Sixty(): HarnessReportRowV3Sixty = HarnessReportRowV3Sixty(
    label = label,
    format = format.name,
    anchorKind = anchorKind,
    strategySource = strategySource,
    buildFailed = buildFailed,
    failureMessage = failureMessage,
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
    allHardMetricsPass = allHardMetricsPass,
    totalScore = totalScore,
    distinctNames = distinctNames,
    fourOfCount = fourOfCount,
    runtimeMs = runtimeMs,
    resolvedMacro = resolvedMacro,
)

object HarnessReportWriterV3Sixty {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun write(harnessDir: File, runId: String, label: String, metrics: List<V3SixtyBuildMetrics>) {
        val reportDir = File(harnessDir, "reports/$runId")
        reportDir.mkdirs()

        val rows = metrics.map { it.toReportRowV3Sixty() }
        val report = HarnessReportV3Sixty(
            runId = runId,
            label = label,
            timestampMs = System.currentTimeMillis(),
            totalSpecs = rows.size,
            passedSpecs = rows.count { it.allHardMetricsPass },
            rows = rows,
        )
        File(reportDir, "report.json").writeText(json.encodeToString(HarnessReportV3Sixty.serializer(), report))
        File(reportDir, "report.md").writeText(renderMarkdown(report))
    }

    private fun renderMarkdown(report: HarnessReportV3Sixty): String = buildString {
        appendLine("# Deck Wizard 60-card wave (v6) harness v1 report — Sixty real-collection (${report.label})")
        appendLine()
        appendLine("Run id: `${report.runId}`  ")
        appendLine("Specs: ${report.totalSpecs}  ")
        appendLine("Passed (all HARD metrics): ${report.passedSpecs} / ${report.totalSpecs}")
        appendLine()

        appendLine("## Aggregate HARD-metric pass counts")
        appendLine()
        appendLine("| Metric | Pass | Fail |")
        appendLine("|---|---|---|")
        val nonFailed = report.rows.filterNot { it.buildFailed }
        appendLine(hardMetricRow("build succeeded", report.rows.count { !it.buildFailed }, report.rows.count { it.buildFailed }))
        appendLine(hardMetricRow("no_blocker", nonFailed.count { it.noBlockerOk }, nonFailed.count { !it.noBlockerOk }))
        appendLine(hardMetricRow("size_or_gaps", nonFailed.count { it.sizeOrGapsOk }, nonFailed.count { !it.sizeOrGapsOk }))
        appendLine(hardMetricRow("determinism", nonFailed.count { it.determinismOk }, nonFailed.count { !it.determinismOk }))
        appendLine(hardMetricRow("quantity_within_max_placeable", nonFailed.count { it.quantityWithinMaxPlaceableOk }, nonFailed.count { !it.quantityWithinMaxPlaceableOk }))
        appendLine(hardMetricRow("legality", nonFailed.count { it.legalityOk }, nonFailed.count { !it.legalityOk }))
        appendLine(hardMetricRow("offplan_share", nonFailed.count { it.offplanShareOk }, nonFailed.count { !it.offplanShareOk }))
        appendLine(hardMetricRow("no_avoidable_role_overflow", nonFailed.count { it.noAvoidableRoleOverflowOk }, nonFailed.count { !it.noAvoidableRoleOverflowOk }))
        appendLine(hardMetricRow("no_color_source_shortage", nonFailed.count { it.noColorSourceShortageOk }, nonFailed.count { !it.noColorSourceShortageOk }))
        appendLine(hardMetricRow("land_target", nonFailed.count { it.landTargetOk }, nonFailed.count { !it.landTargetOk }))
        appendLine()

        if (nonFailed.isNotEmpty()) {
            val scores = nonFailed.map { it.totalScore }.sorted()
            val distinctNames = nonFailed.map { it.distinctNames }.sorted()
            val fourOfCounts = nonFailed.map { it.fourOfCount }.sorted()
            val runtimes = nonFailed.map { it.runtimeMs }.sorted()
            appendLine("## TRACKED — distributions")
            appendLine()
            appendLine("score: min=${scores.first()} p25=${scores[(scores.size * 0.25).toInt().coerceIn(0, scores.size - 1)]} median=${scores[scores.size / 2]} p75=${scores[(scores.size * 0.75).toInt().coerceIn(0, scores.size - 1)]} max=${scores.last()}  ")
            appendLine("distinctNames: min=${distinctNames.first()} median=${distinctNames[distinctNames.size / 2]} max=${distinctNames.last()}  ")
            appendLine("fourOfCount: min=${fourOfCounts.first()} median=${fourOfCounts[fourOfCounts.size / 2]} max=${fourOfCounts.last()}  ")
            appendLine("runtime ms: min=${runtimes.first()} median=${runtimes[runtimes.size / 2]} max=${runtimes.last()}")
            appendLine()
        }

        appendLine("## Worst offenders (first 20 failing specs)")
        appendLine()
        appendLine("| Spec | Fail reasons |")
        appendLine("|---|---|")
        report.rows.filterNot { it.allHardMetricsPass }.take(20).forEach { row ->
            appendLine("| ${row.label} | ${failReasonsV3Sixty(row)} |")
        }
    }

    private fun hardMetricRow(name: String, pass: Int, fail: Int): String = "| $name | $pass | $fail |"

    private fun failReasonsV3Sixty(row: HarnessReportRowV3Sixty): String {
        if (row.buildFailed) return "build failed: ${row.failureMessage}"
        val reasons = mutableListOf<String>()
        if (!row.noBlockerOk) reasons += "blocker"
        if (!row.sizeOrGapsOk) reasons += "size_or_gaps(${row.actualSize}+${row.gapsTotal})"
        if (!row.determinismOk) reasons += "determinism"
        if (!row.quantityWithinMaxPlaceableOk) reasons += "quantity(${row.quantityViolations.joinToString(",")})"
        if (!row.legalityOk) reasons += "legality(${row.legalityViolations.joinToString(",")})"
        if (!row.offplanShareOk) reasons += "offplan_share(${"%.2f".format(row.offplanShare)})"
        if (!row.noAvoidableRoleOverflowOk) reasons += "role_overflow(${row.roleOverflowViolations.joinToString(",")})"
        if (!row.noColorSourceShortageOk) reasons += "color_source_shortage"
        if (!row.landTargetOk) reasons += "land_target(${row.landCount})"
        return reasons.joinToString(", ")
    }
}
