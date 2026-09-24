package com.mmg.manahub.feature.decks.presentation.components
// COMMENTS_REVIEWED: 2026-09-16

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
import com.mmg.manahub.core.ui.components.SectionHeader
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.AxisKey
import com.mmg.manahub.feature.decks.domain.engine.CardSection
import com.mmg.manahub.feature.decks.domain.engine.SynergyEngine
import com.mmg.manahub.feature.decks.domain.engine.SynergyEngineState

// ═══════════════════════════════════════════════════════════════════════════════
//  "Engines" (Deck Wizard Commander v5, D2/D3) — one card per producer -> payoff pair the deck
//  actually has, ordered per D2 ahead of the deck's tribe and the interaction/standalone/off-plan
//  split (rendered separately by DeckStudioScreen's generic CardSectionRow list). Real counts and
//  Browse come from the SAME engine:<axis>:producers/payoffs CardSections AnalysisEngine emits --
//  never re-derived here, so this view and the generic section list can never disagree.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Renders the SYNERGY pillar's Engines list. Handles the 3 states the gate calls for:
 *  - [notApplicable] (P4 has literally zero graph edges) — an honest "no detectable synergy plan".
 *  - engines that exist but none clears the D3 visibility rule — a lighter explanatory note.
 *  - one or more engines — one [SynergyEngineCard] each, in [engines]' own order.
 */
@Composable
fun SynergyPackagesSection(
    engines: List<SynergyEngine>,
    sections: List<CardSection>,
    notApplicable: Boolean,
    resolveCard: (String) -> Card?,
    onCardClick: (String) -> Unit,
    onBrowseSection: (CardSection) -> Unit,
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

    if (engines.isEmpty()) {
        Surface(
            shape = CardShape,
            color = mc.backgroundSecondary,
            border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.5f)),
            modifier = modifier.fillMaxWidth(),
        ) {
            EmptyState(
                title = stringResource(R.string.deck_analysis_synergy_no_live_engines_title),
                subtitle = stringResource(R.string.deck_analysis_synergy_no_live_engines_body),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }

    val expandedAxes = remember { mutableStateMapOf<AxisKey, Boolean>() }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        engines.forEach { engine ->
            // AnalysisEngine always emits both sides of an engine axis from the same graph pass -- a
            // miss here means the two have drifted apart, so this fails loudly instead of silently
            // rendering a band-less section.
            val producerSection = sections.first { it.id == "engine:${engine.axis}:producers" }
            val payoffSection = sections.first { it.id == "engine:${engine.axis}:payoffs" }
            SynergyEngineCard(
                engine = engine,
                producerSection = producerSection,
                payoffSection = payoffSection,
                resolveCard = resolveCard,
                onCardClick = onCardClick,
                onBrowseProducers = { onBrowseSection(producerSection) },
                onBrowsePayoffs = { onBrowseSection(payoffSection) },
                expanded = expandedAxes[engine.axis] ?: true,
                onToggleExpanded = { expandedAxes[engine.axis] = !(expandedAxes[engine.axis] ?: true) },
            )
        }
    }
}

/** One axis' engine card: title, a plain-language sentence, real "N producers -> M payoffs"
 * counts, an incomplete badge when [SynergyEngine.state] isn't [SynergyEngineState.COMPLETE], then
 * the producers row, a directional arrow, and the payoffs row -- both with Browse. A null
 * [payoffSection] renders a producers-only engine (payoff-optional axes such as MILL_OPP/LOCK). */
@Composable
internal fun SynergyEngineCard(
    engine: SynergyEngine,
    producerSection: CardSection,
    payoffSection: CardSection?,
    resolveCard: (String) -> Card?,
    onCardClick: (String) -> Unit,
    onBrowseProducers: () -> Unit,
    onBrowsePayoffs: () -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    var producersExpanded by remember(engine.axis) { mutableStateOf(true) }
    var payoffsExpanded by remember(engine.axis) { mutableStateOf(true) }

    Surface(
        shape = CardShape,
        color = mc.backgroundSecondary,
        border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(spacing.sm)) {
            SectionHeader(
                title = stringResource(R.string.deck_analysis_engine_title, engine.axis.axisDisplayLabel()),
                expanded = expanded,
                onToggle = onToggleExpanded,
                titleColor = mc.textPrimary,
                trailing = {
                    if (engine.state != SynergyEngineState.COMPLETE) {
                        val badgeText = if (engine.state == SynergyEngineState.MISSING_PAYOFFS) {
                            stringResource(R.string.deck_analysis_engine_missing_payoffs)
                        } else {
                            stringResource(R.string.deck_analysis_engine_missing_producers)
                        }
                        // goldMtg text on a 15%-goldMtg tonal fill drops below 4.5:1 on HallowedPrint
                        // (3.24:1) -- a bordered surface with textPrimary ink clears AA on every theme.
                        Surface(shape = ChipShape, color = mc.backgroundSecondary, border = BorderStroke(1.dp, mc.goldMtg)) {
                            Text(
                                text = badgeText,
                                style = ty.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = mc.textPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                },
            )
            Text(
                text = axisEngineSentence(engine.axis),
                style = ty.bodySmall,
                color = mc.textSecondary,
                modifier = Modifier.padding(top = spacing.xxs, bottom = spacing.xs),
            )
            Text(
                text = if (payoffSection != null) {
                    stringResource(R.string.deck_analysis_engine_counts, producerSection.realCount, payoffSection.realCount)
                } else {
                    stringResource(R.string.deck_inspirations_producers_only_counts, producerSection.realCount)
                },
                style = ty.labelSmall,
                color = mc.textSecondary,
                modifier = Modifier.padding(bottom = spacing.xs),
            )

            if (expanded) {
                CardSectionRow(
                    section = producerSection.copy(label = stringResource(R.string.deck_analysis_axis_producers_label)),
                    resolveCard = resolveCard,
                    onCardClick = onCardClick,
                    onBrowse = onBrowseProducers,
                    expanded = producersExpanded,
                    onToggleExpanded = { producersExpanded = !producersExpanded },
                )

                if (payoffSection != null) {
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

                    CardSectionRow(
                        section = payoffSection.copy(label = stringResource(R.string.deck_analysis_axis_payoffs_label)),
                        resolveCard = resolveCard,
                        onCardClick = onCardClick,
                        onBrowse = onBrowsePayoffs,
                        expanded = payoffsExpanded,
                        onToggleExpanded = { payoffsExpanded = !payoffsExpanded },
                    )
                }
            }
        }
    }
}

@Composable
private fun axisEngineSentence(axis: AxisKey): String = when (axis) {
    "LIFE" -> stringResource(R.string.deck_analysis_engine_sentence_life)
    "DEATH" -> stringResource(R.string.deck_analysis_engine_sentence_death)
    "TOKENS" -> stringResource(R.string.deck_analysis_engine_sentence_tokens)
    "COUNTERS" -> stringResource(R.string.deck_analysis_engine_sentence_counters)
    "LANDFALL" -> stringResource(R.string.deck_analysis_engine_sentence_landfall)
    "GRAVEYARD" -> stringResource(R.string.deck_analysis_engine_sentence_graveyard)
    "ETB" -> stringResource(R.string.deck_analysis_engine_sentence_etb)
    "SPELLS" -> stringResource(R.string.deck_analysis_engine_sentence_spells)
    "ARTIFACTS" -> stringResource(R.string.deck_analysis_engine_sentence_artifacts)
    "ENCHANTMENTS" -> stringResource(R.string.deck_analysis_engine_sentence_enchantments)
    "ATTACHED" -> stringResource(R.string.deck_analysis_engine_sentence_attached)
    "ATTACK" -> stringResource(R.string.deck_analysis_engine_sentence_attack)
    "PLANESWALKERS" -> stringResource(R.string.deck_analysis_engine_sentence_planeswalkers)
    "GROUP" -> stringResource(R.string.deck_analysis_engine_sentence_group)
    "ENGINE" -> stringResource(R.string.deck_analysis_engine_sentence_engine)
    else -> stringResource(R.string.deck_analysis_engine_sentence_generic)
}
