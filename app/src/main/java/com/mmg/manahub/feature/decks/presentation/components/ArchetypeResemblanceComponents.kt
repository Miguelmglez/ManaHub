package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.SectionHeader
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.usecase.ArchetypeResolution
import com.mmg.manahub.feature.decks.domain.usecase.MacroResemblance
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Analysis Engine v3, Phase 5 (UI) — the "Archetype signal" card: macro (or an honest
//  hybrid/"Custom" label) + optional posture + up to 2 themes, the confidence margin, and the
//  full 5-way resemblance profile. Reads [ArchetypeResolution] directly (NOT
//  [com.mmg.manahub.feature.decks.domain.engine.ResolvedStrategyInfo] — that type's
//  `resemblance`/hybrid-label wiring isn't threaded through the real analysis call site; every
//  input this card needs — macro/posture/themes/confidence/resemblance — is already on
//  [ArchetypeResolution] via [com.mmg.manahub.feature.decks.domain.engine.DeckHealth
//  .archetypeResolution], fully populated on the real inference path). Complements, does not
//  replace, [StrategyPlanChip] (`CuratedStrategyPickerSheet.kt`) — that chip shows the matched
//  CURATED strategy name; this card shows the raw inferred signal underneath it, including for
//  decks with no curated match at all.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * The "Archetype signal" card (Deck Analysis Engine v3, spec §2.1/§3): a collapsible header
 * (plan/hybrid label + confidence caption) with posture/theme badges always visible, and the full
 * ranked [ArchetypeResolution.resemblance] profile revealed on expand. Never renders a "no plan"
 * bucket — an ambiguous resolution still shows a real hybrid label and the full resemblance list
 * (see [ArchetypeResolution.planLabel]'s own KDoc).
 */
@Composable
fun ArchetypeResemblanceCard(
    resolution: ArchetypeResolution,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val isAmbiguous = resolution.macro == null
    val planLabel = resolution.planLabel()

    Surface(
        shape = CardShape,
        color = mc.backgroundSecondary,
        border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(spacing.sm)) {
            SectionHeader(
                title = stringResource(R.string.deck_analysis_archetype_header),
                expanded = expanded,
                onToggle = onToggleExpanded,
                trailing = {
                    Text(
                        text = planLabel,
                        style = ty.labelLarge,
                        color = if (isAmbiguous) mc.textSecondary else mc.primaryAccent,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = spacing.xs),
                    )
                },
            )

            if (isAmbiguous) {
                Text(
                    text = stringResource(R.string.deck_analysis_archetype_ambiguous_caption),
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                    modifier = Modifier.padding(bottom = spacing.xs),
                )
            } else {
                Text(
                    text = stringResource(R.string.deck_analysis_archetype_confidence_margin, (resolution.confidence * 100).roundToInt()),
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                    modifier = Modifier.padding(bottom = spacing.xs),
                )
            }

            if (resolution.posture != null || resolution.themes.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    modifier = Modifier.padding(bottom = spacing.xs),
                ) {
                    resolution.posture?.let {
                        PlanBadge(text = stringResource(R.string.deck_analysis_posture_prefix, it.displayName), color = mc.secondaryAccent)
                    }
                    resolution.themes.take(2).forEach { theme ->
                        PlanBadge(text = theme.displayName, color = mc.goldMtg)
                    }
                }
            }

            if (expanded) {
                if (resolution.resemblance.isEmpty()) {
                    Text(
                        text = stringResource(R.string.deck_analysis_section_empty),
                        style = ty.labelSmall,
                        color = mc.textDisabled,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        resolution.resemblance.forEachIndexed { index, item ->
                            ResemblanceRow(item = item, isTop = index == 0)
                        }
                    }
                }
            }
        }
    }
}

/** Small pill badge for a posture/theme tag — mirrors [StrategyPlanChip]'s own MANUAL/AUTO badge
 * shape but through the real [ChipShape] token (that pre-existing badge predates this file and
 * uses a raw `RoundedCornerShape(4.dp)` — left as-is, not part of this pass's scope). Posture/theme
 * are not a [com.mmg.manahub.core.model.CardTag], so this is a plain badge, not `CardTagChip`. */
@Composable
private fun PlanBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    val ty = MaterialTheme.magicTypography
    Surface(shape = ChipShape, color = color.copy(alpha = 0.15f), modifier = modifier) {
        Text(
            text = text,
            style = ty.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.sm, vertical = MaterialTheme.spacing.xxs),
        )
    }
}

/**
 * One row of the resemblance profile ("46% Control"): a label, a proportional bar, and the
 * percentage. The TOP entry (index 0, [isTop]) is rendered in the primary accent + bold to draw
 * the eye to the dominant match; every other entry is muted ([MagicColors.textSecondary] text,
 * [MagicColors.surfaceVariant] bar) — a rank-based visual weight instead of a 5-color categorical
 * palette (no named shape/color token maps cleanly to "one hue per archetype" without inventing a
 * new one; see [RoleCoverageEntryRow]'s own documented note on the same thin-track-shape gap this
 * bar shares).
 */
@Composable
private fun ResemblanceRow(item: MacroResemblance, isTop: Boolean, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val pct = (item.share * 100).roundToInt()
    val barColor = if (isTop) mc.primaryAccent else mc.surfaceVariant
    val textColor = if (isTop) mc.textPrimary else mc.textSecondary

    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.macro.displayName,
            style = ty.bodySmall,
            color = textColor,
            fontWeight = if (isTop) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(88.dp),
        )
        Spacer(Modifier.width(spacing.sm))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(mc.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(item.share.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor),
            )
        }
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = stringResource(R.string.deck_analysis_resemblance_row_format, pct),
            style = ty.labelMedium,
            color = textColor,
            fontWeight = if (isTop) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(40.dp),
        )
    }
}
