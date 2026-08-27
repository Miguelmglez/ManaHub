package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MiniProgressRing
import com.mmg.manahub.core.ui.components.SectionHeader
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.AxisCardBreakdown
import com.mmg.manahub.feature.decks.domain.engine.AxisKey
import com.mmg.manahub.feature.decks.domain.engine.AxisState
import com.mmg.manahub.feature.decks.domain.engine.CardSection
import com.mmg.manahub.feature.decks.domain.engine.DeckSynergyGraph
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Analysis Engine v3, Phase 5 (UI) — "Synergy packages": one card per LIVE axis, showing the
//  actual producer cards feeding the actual payoff cards on that axis. This is the plan's own
//  "highest user-visible value" surface ("you have 9 token generators and 0 payoffs" is real,
//  actionable deckbuilding advice the engine could not give before this model existed) — reads
//  [DeckAnalysis.debugSynergyGraph] (populated on the real Analysis tab pass as of this phase, see
//  that field's own updated KDoc) directly; never re-derives producer/payoff membership from cards
//  itself (that classification lives in [com.mmg.manahub.feature.decks.domain.engine.SynergyGraph],
//  which already computed it into [DeckSynergyGraph.axisBreakdown]).
//
//  Reuses [CardSectionRow] (`CardSectionComponents.kt`) for the actual card rows — a "producers"
//  and a "payoffs" [CardSection] are built ad hoc here (never added to
//  [com.mmg.manahub.feature.decks.domain.engine.PillarResult.sections] — these are graph-derived,
//  display-only groupings distinct from that pillar's own fingerprint/interaction/standalone/
//  offplan sections, which keep rendering unchanged below this block).
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Renders the whole synergy-package list for the SYNERGY pillar detail. Handles the 3 states the
 * gate calls for explicitly:
 *  - [notApplicable] (P4 has literally zero graph edges) — an honest "no detectable synergy plan"
 *    explanation, never an empty list and never implying a score of 0.
 *  - a graph with edges but no axis clearing the live threshold — a lighter, non-alarming note
 *    (this deck has *some* graph signal, just nothing dominant enough to call a "package" yet).
 *  - one or more live axes — one [SynergyAxisCard] each, in [DeckSynergyGraph.axes] declaration
 *    order (mirrors the engine's own display-order convention).
 */
@Composable
fun SynergyPackagesSection(
    graph: DeckSynergyGraph?,
    notApplicable: Boolean,
    resolveCard: (String) -> Card?,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing

    if (notApplicable) {
        Surface(
            shape = CardShape,
            color = mc.backgroundSecondary,
            border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.5f)),
            modifier = modifier.fillMaxWidth(),
        ) {
            EmptyState(
                title = stringResource(R.string.deck_analysis_synergy_not_applicable_title),
                subtitle = stringResource(R.string.deck_analysis_synergy_not_applicable_body),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }

    val liveAxes = graph?.axes.orEmpty().filter { it.isLive }
    if (liveAxes.isEmpty()) {
        Text(
            text = stringResource(R.string.deck_analysis_synergy_no_live_packages),
            style = MaterialTheme.magicTypography.bodySmall,
            color = mc.textSecondary,
            modifier = modifier.fillMaxWidth().padding(vertical = spacing.sm),
        )
        return
    }

    // Per-axis outer expand/collapse — owned here (not hoisted to the caller) so the SYNERGY
    // pillar detail integration stays a one-line call; every axis starts expanded (the plan's own
    // framing: this is the highest-value surface in the redesign, it should not default-hide).
    val expandedAxes = remember { mutableStateMapOf<AxisKey, Boolean>() }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        liveAxes.forEach { axisState ->
            val breakdown = graph?.axisBreakdown?.get(axisState.axis) ?: AxisCardBreakdown(emptyList(), emptyList())
            SynergyAxisCard(
                axisState = axisState,
                breakdown = breakdown,
                resolveCard = resolveCard,
                onCardClick = onCardClick,
                expanded = expandedAxes[axisState.axis] ?: true,
                onToggleExpanded = { expandedAxes[axisState.axis] = !(expandedAxes[axisState.axis] ?: true) },
            )
        }
    }
}

/**
 * One live axis's package card: a header (axis label, LIVE badge, health ring), a plain-English
 * producer/payoff/health caption, and — while [expanded] — the actual producer cards feeding the
 * actual payoff cards, with a directional arrow between the two rows so the producer -> payoff
 * relationship (the entire point of this model) is visually unambiguous rather than just two
 * unlabeled card rows stacked on top of each other.
 */
@Composable
private fun SynergyAxisCard(
    axisState: AxisState,
    breakdown: AxisCardBreakdown,
    resolveCard: (String) -> Card?,
    onCardClick: (String) -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    // qualityColor is `internal` in HealthComponents.kt (same package) — the same good/mid/low ramp
    // every other health-driven color in this feature already uses.
    val healthColor = mc.qualityColor(axisState.health)
    var producersExpanded by remember(axisState.axis) { mutableStateOf(true) }
    var payoffsExpanded by remember(axisState.axis) { mutableStateOf(true) }

    Surface(
        shape = CardShape,
        color = mc.backgroundSecondary,
        border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(spacing.sm)) {
            SectionHeader(
                title = stringResource(R.string.deck_analysis_axis_package_header, axisState.axis.axisDisplayLabel()),
                expanded = expanded,
                onToggle = onToggleExpanded,
                titleColor = mc.textPrimary,
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        modifier = Modifier.padding(end = spacing.xs),
                    ) {
                        Surface(shape = ChipShape, color = mc.lifePositive.copy(alpha = 0.15f)) {
                            Text(
                                text = stringResource(R.string.deck_analysis_axis_live_badge),
                                style = ty.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = mc.lifePositive,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        // contentDescription left null -- the caption Text right below already
                        // states the same producer/payoff/health numbers accessibly; a second
                        // announcement here would just double-read the same info.
                        MiniProgressRing(value = axisState.health, color = healthColor)
                    }
                },
            )
            Text(
                text = stringResource(
                    R.string.deck_analysis_axis_health_caption,
                    axisState.producerCopies,
                    axisState.payoffCopies,
                    (axisState.health * 100).roundToInt(),
                ),
                style = ty.labelSmall,
                color = mc.textSecondary,
                modifier = Modifier.padding(bottom = spacing.xs),
            )

            if (expanded) {
                val producersLabel = stringResource(R.string.deck_analysis_axis_producers_label)
                CardSectionRow(
                    section = CardSection(
                        id = "axis:${axisState.axis}:producers",
                        label = producersLabel,
                        current = breakdown.producers.sumOf { it.quantity },
                        contributions = breakdown.producers,
                    ),
                    resolveCard = resolveCard,
                    onCardClick = onCardClick,
                    onBrowse = null,
                    expanded = producersExpanded,
                    onToggleExpanded = { producersExpanded = !producersExpanded },
                )

                // Directional divider — makes the producer -> payoff relationship legible instead
                // of two unlabeled card rows stacked on top of each other (the model's whole point).
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xxs),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = mc.textDisabled,
                        modifier = Modifier.padding(vertical = 2.dp).rotate(90f),
                    )
                }

                val payoffsLabel = stringResource(R.string.deck_analysis_axis_payoffs_label)
                CardSectionRow(
                    section = CardSection(
                        id = "axis:${axisState.axis}:payoffs",
                        label = payoffsLabel,
                        current = breakdown.payoffs.sumOf { it.quantity },
                        contributions = breakdown.payoffs,
                    ),
                    resolveCard = resolveCard,
                    onCardClick = onCardClick,
                    onBrowse = null,
                    expanded = payoffsExpanded,
                    onToggleExpanded = { payoffsExpanded = !payoffsExpanded },
                )
            }
        }
    }
}
