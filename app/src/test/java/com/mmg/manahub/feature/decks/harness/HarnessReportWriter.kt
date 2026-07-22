package com.mmg.manahub.feature.decks.harness

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

// ═══════════════════════════════════════════════════════════════════════════════
//  Wizard Quality Campaign -- Phase H harness. Writes testdata/wizard-harness/reports/<runId>/
//  report.json (machine-readable) + report.md (human summary the main agent actually reads).
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class HarnessReportRow(
    val label: String,
    val format: String,
    @SerialName("build_failed") val buildFailed: Boolean,
    @SerialName("failure_message") val failureMessage: String? = null,
    @SerialName("size_ok") val sizeOk: Boolean,
    @SerialName("actual_full_size") val actualFullSize: Int,
    @SerialName("target_full_size") val targetFullSize: Int,
    val shortfall: Int,
    @SerialName("legality_ok") val legalityOk: Boolean,
    @SerialName("illegal_cards") val illegalCards: List<String>,
    @SerialName("duplicate_name_violations") val duplicateNameViolations: List<String>,
    @SerialName("over_owned_violations") val overOwnedViolations: List<String>,
    @SerialName("off_color_identity_violations") val offColorIdentityViolations: List<String>,
    @SerialName("determinism_ok") val determinismOk: Boolean,
    @SerialName("land_count") val landCount: Int,
    @SerialName("land_share_percent") val landSharePercent: Double,
    @SerialName("lands_ok") val landsOk: Boolean,
    @SerialName("coherence_cuts_v2_ok") val coherenceCutsV2Ok: Boolean,
    @SerialName("coherence_cuts_v2_violation_names") val coherenceCutsV2ViolationNames: List<String>,
    @SerialName("coherence_swaps_ok") val coherenceSwapsOk: Boolean,
    @SerialName("coherence_swaps_violation_names") val coherenceSwapsViolationNames: List<String>,
    @SerialName("coherence_adds_ok") val coherenceAddsOk: Boolean,
    @SerialName("adds_violation_names") val addsViolationNames: List<String>,
    @SerialName("commander_present_ok") val commanderPresentOk: Boolean,
    @SerialName("all_hard_metrics_pass") val allHardMetricsPass: Boolean,
    @SerialName("overall_score") val overallScore: Float,
    @SerialName("warning_counts") val warningCounts: Map<String, Int>,
    @SerialName("category_fill_percent") val categoryFillPercent: Double,
)

@Serializable
data class HarnessReport(
    @SerialName("run_id") val runId: String,
    val label: String,
    @SerialName("timestamp_ms") val timestampMs: Long,
    @SerialName("total_specs") val totalSpecs: Int,
    @SerialName("passed_specs") val passedSpecs: Int,
    val rows: List<HarnessReportRow>,
)

fun BuildMetrics.toReportRow(): HarnessReportRow = HarnessReportRow(
    label = label,
    format = format.name,
    buildFailed = buildFailed,
    failureMessage = failureMessage,
    sizeOk = sizeOk,
    actualFullSize = actualFullSize,
    targetFullSize = targetFullSize,
    shortfall = shortfall,
    legalityOk = legalityOk,
    illegalCards = illegalCards,
    duplicateNameViolations = duplicateNameViolations,
    overOwnedViolations = overOwnedViolations,
    offColorIdentityViolations = offColorIdentityViolations,
    determinismOk = determinismOk,
    landCount = landCount,
    landSharePercent = landSharePercent,
    landsOk = landsOk,
    coherenceCutsV2Ok = coherenceCutsV2Ok,
    coherenceCutsV2ViolationNames = coherenceCutsV2ViolationNames,
    coherenceSwapsOk = coherenceSwapsOk,
    coherenceSwapsViolationNames = coherenceSwapsViolationNames,
    coherenceAddsOk = coherenceAddsOk,
    addsViolationNames = addsViolationNames,
    commanderPresentOk = commanderPresentOk,
    allHardMetricsPass = allHardMetricsPass,
    overallScore = overallScore,
    warningCounts = warningCounts,
    categoryFillPercent = categoryFillPercent,
)

object HarnessReportWriter {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun write(harnessDir: File, runId: String, label: String, metrics: List<BuildMetrics>) {
        val reportDir = File(harnessDir, "reports/$runId")
        reportDir.mkdirs()

        val rows = metrics.map { it.toReportRow() }
        val report = HarnessReport(
            runId = runId,
            label = label,
            timestampMs = System.currentTimeMillis(),
            totalSpecs = rows.size,
            passedSpecs = rows.count { it.allHardMetricsPass },
            rows = rows,
        )
        File(reportDir, "report.json").writeText(json.encodeToString(HarnessReport.serializer(), report))
        File(reportDir, "report.md").writeText(renderMarkdown(report))
    }

    private fun renderMarkdown(report: HarnessReport): String = buildString {
        appendLine("# Wizard Quality Campaign harness report — `${report.label}`")
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
        appendLine(hardMetricRow("size", nonFailed.count { it.sizeOk }, nonFailed.count { !it.sizeOk }))
        appendLine(hardMetricRow("legality", nonFailed.count { it.legalityOk }, nonFailed.count { !it.legalityOk }))
        appendLine(hardMetricRow("determinism", nonFailed.count { it.determinismOk }, nonFailed.count { !it.determinismOk }))
        appendLine(hardMetricRow("lands", nonFailed.count { it.landsOk }, nonFailed.count { !it.landsOk }))
        appendLine(hardMetricRow("coherence-cuts-v2", nonFailed.count { it.coherenceCutsV2Ok }, nonFailed.count { !it.coherenceCutsV2Ok }))
        appendLine(hardMetricRow("coherence-swaps", nonFailed.count { it.coherenceSwapsOk }, nonFailed.count { !it.coherenceSwapsOk }))
        appendLine(hardMetricRow("coherence-adds", nonFailed.count { it.coherenceAddsOk }, nonFailed.count { !it.coherenceAddsOk }))
        appendLine(hardMetricRow("commander-present", nonFailed.count { it.commanderPresentOk }, nonFailed.count { !it.commanderPresentOk }))
        appendLine()

        appendLine("## Worst offenders (first 20 failing specs)")
        appendLine()
        appendLine("| Spec | Format | Fail reasons |")
        appendLine("|---|---|---|")
        report.rows.filterNot { it.allHardMetricsPass }.take(20).forEach { row ->
            appendLine("| ${row.label} | ${row.format} | ${failReasons(row)} |")
        }
        appendLine()

        appendLine("## Per-spec table")
        appendLine()
        appendLine("| Spec | Format | Size | Legal | Determ. | Lands | Cuts-v2✗ | Swaps✗ | Adds✗ | Cmdr | Score | Pass |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|---|---|")
        report.rows.forEach { row ->
            if (row.buildFailed) {
                appendLine("| ${row.label} | ${row.format} | FAILED: ${row.failureMessage} | | | | | | | | | ✗ |")
            } else {
                appendLine(
                    "| ${row.label} | ${row.format} | ${row.actualFullSize}/${row.targetFullSize} (short ${row.shortfall}) | " +
                        "${bool(row.legalityOk)} | ${bool(row.determinismOk)} | ${bool(row.landsOk)} (${"%.1f".format(row.landSharePercent)}%) | " +
                        "${row.coherenceCutsV2ViolationNames.size} | ${row.coherenceSwapsViolationNames.size} | " +
                        "${row.addsViolationNames.size} | ${bool(row.commanderPresentOk)} | ${"%.2f".format(row.overallScore)} | ${bool(row.allHardMetricsPass)} |"
                )
            }
        }
    }

    private fun hardMetricRow(name: String, pass: Int, fail: Int): String = "| $name | $pass | $fail |"
    private fun bool(v: Boolean): String = if (v) "✓" else "✗"

    private fun failReasons(row: HarnessReportRow): String {
        val reasons = mutableListOf<String>()
        if (!row.sizeOk) reasons += "size(${row.actualFullSize}/${row.targetFullSize})"
        if (!row.legalityOk) reasons += "legality"
        if (!row.determinismOk) reasons += "determinism"
        if (!row.landsOk) reasons += "lands(${"%.1f".format(row.landSharePercent)}%)"
        if (!row.coherenceCutsV2Ok) reasons += "coherence-cuts-v2(${row.coherenceCutsV2ViolationNames.joinToString(",")})"
        if (!row.coherenceSwapsOk) reasons += "coherence-swaps(${row.coherenceSwapsViolationNames.joinToString(",")})"
        if (!row.coherenceAddsOk) reasons += "coherence-adds(${row.addsViolationNames.size})"
        if (!row.commanderPresentOk) reasons += "commander-missing"
        return reasons.joinToString(", ")
    }
}
